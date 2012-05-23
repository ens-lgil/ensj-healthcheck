#!/usr/bin/env perl

# Propagate manual_ok_all_releases from one release to another

use strict;
use warnings;
use DBI;
use Getopt::Long;
use IO::File;
use Carp;
use File::Basename;
use Data::Dumper;

$Data::Dumper::Sortkeys = 1;

my (
  $host1,     $port1,     $user1,  $pass1,       $host2,
  $port2,     $user2,     $pass2,  $host_prev,   $port_prev,
  $user_prev, $pass_prev, $dbname, $old_release, $new_release,
  $quiet,     $new_dbname
);

GetOptions(
  'user1=s'       => \$user1,
  'pass1=s'       => \$pass1,
  'host1=s'       => \$host1,
  'port1=i'       => \$port1,
  'user2=s'       => \$user2,
  'pass2=s'       => \$pass2,
  'host2=s'       => \$host2,
  'port2=i'       => \$port2,
  'user_prev=s'   => \$user_prev,
  'pass_prev=s'   => \$pass_prev,
  'host_prev=s'   => \$host_prev,
  'port_prev=i'   => \$port_prev,
  'dbname=s'      => \$dbname,
  'old_release=i' => \$old_release,
  'new_release=i' => \$new_release,
  'quiet'         => \$quiet,
  'new_dbname=s'  => \$new_dbname,
  'help'          => sub { usage(); exit(0); }
);

$user1 = $user1 || "ensadmin";
$pass1 = $pass1 || "ensembl";
$host1 = $host1 || "ens-staging";
$port1 = $port1 || "3306";

$user2 = $user2 || "ensadmin";
$pass2 = $pass2 || "ensembl";
$host2 = $host2 || "ens-staging2";
$port2 = $port2 || "3306";

$user_prev = $user_prev || "ensadmin";
$pass_prev = $pass_prev || "ensembl";
$host_prev = $host_prev || "ens-livemirror";
$port_prev = $port_prev || "3306";

# Note healthchecks db ($dbname) is assumed to be on $host1
$dbname = $dbname || "healthchecks";

if ( !( $old_release && $new_release ) ) {
  print "Must specify -old_release and -new_release\n";
  exit(1);
}

# connect to healthcheck database - note assumed to be on host1
my $dbi1 = DBI->connect( "DBI:mysql:host=$host1:port=$port1;database=$dbname",
  $user1, $pass1, { 'RaiseError' => 1 } )
  || die "Can't connect to healthcheck database\n";

# connect to second database server if it's defined
my $dbi2 = undef;
if ($host2) {
  $dbi2 = DBI->connect( "DBI:mysql:host=$host2:port=$port2;",
    $user2, $pass2, { 'RaiseError' => 1 } )
    || die "Can't connect to secondary database on $host2:port2\n";
}

# connect to server where previous release databases should be
my $dbi_prev = DBI->connect( "DBI:mysql:host=$host_prev:port=$port_prev",
  $user_prev, $pass_prev, { 'RaiseError' => 1 } )
  || die "Can't connect to previous release database server\n";

# cache database name mappings
my $old_to_new_db_name;
my $session_id;

# if we are propagating all the databases within the release, get all databases from staging
if ( !$new_dbname ) {

  $old_to_new_db_name =
    create_db_name_cache( $dbi1, $dbi1, $old_release, $new_release );

  # add second database server list if required
  if ($host2) {

    my $second_server_dbs =
      create_db_name_cache( $dbi1, $dbi2, $old_release, $new_release );
    $old_to_new_db_name = { %$old_to_new_db_name, %$second_server_dbs
    };    # note use of {} since we're dealing with references

  }

  # create a new session for new release or reuse an existing one
  $session_id = session_id( $dbi1, $old_release, $new_release );

}
else {

  # we are propagating a single database
  $old_to_new_db_name =
    create_db_name_cache( $dbi1, $dbi1, $old_release, $new_release,
    $new_dbname );

  # add second database server list if required
  if ($host2) {
    my $second_server_dbs =
      create_db_name_cache( $dbi1, $dbi2, $old_release, $new_release,
      $new_dbname );
    $old_to_new_db_name = { %$old_to_new_db_name, %$second_server_dbs
    };    # note use of {} since we're dealing with references

  }

  # we will use the latest session_id (if it has already been created)
  $session_id = get_latest_session_id( $dbi1, $new_release );
  if ( $session_id < 0 ) {

    # the session_id is not correct, probably not run propagate before;
    print STDERR
"There is no session_id for release $new_release in the database\n. Have you propagated all the databases before?\n";
    exit(1);
  }
}

# cache genebuild.start_date entries for all databases
my $meta_cache = create_meta_cache( $dbi1, $dbi2, $dbi_prev );

# cache assembly.name for all DBs 
my $assembly_cache = create_assembly_cache( $dbi1, $dbi2, $dbi_prev );

#Meta cache for regulation
my $regulatory_build_cache = create_regulation_cache($dbi1, $dbi2, $dbi_prev);

# propagate! propagate!
propagate( $dbi1, $dbi_prev, $old_release, $new_release);

set_end_time( $dbi1 );

# --------------------------------------------------------------------------------

sub create_db_name_cache {
  my ( $healthcheck_dbi, $server_dbi, $old_release, $new_release, $new_dbname )
    = @_;

  # get list of databases for old session
  my $sql = "SELECT distinct(database_name) from report WHERE database_name LIKE ?";
  my $hc_like;
  if ($new_dbname) {
    # only propagate for new_dbname
    $new_dbname =~ /([a-z]+_[a-z]+_[a-z]+)_\d+/;
    $hc_like = "%$1_$old_release%";
  }
  else {
    # propagate for all databases in old_release
    $hc_like = "%_${old_release}_%";
  }
  my @old_dbs = @{$healthcheck_dbi->selectcol_arrayref($sql, {}, $hc_like)};
  my @new_dbs = @{get_new_databases($server_dbi, $new_release, $new_dbname)};

  # create mapping
  my %cache;
  foreach my $old_db (@old_dbs) {
    my $new_db = find_match( $old_db, @new_dbs );
    if ($new_db) {
      print "$old_db -> $new_db\n" unless ($quiet);
      $cache{$old_db} = $new_db;
    }
  }
  
  return \%cache;
}

# --------------------------------------------------------------------------------

sub get_new_databases {
  my ($server_dbi, $new_release, $new_dbname) = @_;
  my $new_like = ($new_dbname) ? 
    "%$new_dbname%" :     # get a single DB
    "%_${new_release}_%"; # get all DBs; note this will exclude master_schema_48 etc
  my $new_dbs = $server_dbi->selectcol_arrayref("SHOW DATABASES LIKE ?", {}, $new_like);
  return $new_dbs;
}

# --------------------------------------------------------------------------------
# Returns databases which could be parsed by the extract_prefix code
#

sub get_valid_databases {
  my ($server_dbi, $release) = @_;
  my @databases = grep {
    (extract_prefix($_)) ? 1 : 0
  } @{get_new_databases($server_dbi, $release)};
  return \@databases;
}

# --------------------------------------------------------------------------------

sub find_match {
  my ($old_db, @new_dbs) = @_;
  foreach my $new_db (@new_dbs) {
    return $new_db if compare_dbs($old_db, $new_db);
  }
  return;
}

# --------------------------------------------------------------------------------

sub compare_dbs {
  my ( $old, $new ) = @_;
  return extract_prefix($old) eq extract_prefix($new);
}

# --------------------------------------------------------------------------------

sub extract_prefix {
  my ($dbname) = @_;
  my $regex = qr/(
    \A [a-z0-9]+ _ [a-z0-9]+  #binomial component 
    (?: _ [a-z0-9]+)?         #trinomial component or group
    _ [a-z]+                  #group
    ) _ \d                    # _ release number of some description
  /xms;
  my ($prefix) = $dbname =~ $regex;
  return $prefix;
}

# --------------------------------------------------------------------------------

sub propagate {
  my ($dbi, $dbi_prev, $old_release, $new_release) = @_;

  my %propagated_new_databases;

  my @types =
    qw(manual_ok_all_releases manual_ok_this_assembly manual_ok_this_genebuild manual_ok_this_regulatory_build);

  my $select_sth = $dbi->prepare(
"SELECT r.first_session_id, r.species, r.database_type, r.timestamp, r.testcase, r.result, r.text, a.person, a.action,a.comment,a.created_at, a.modified_at, a.created_by, a.modified_by FROM report r, annotation a WHERE a.report_id=r.report_id AND r.database_name=? AND a.action=?"
  );

  my $insert_report_sth = $dbi->prepare(
"INSERT INTO report (first_session_id, last_session_id, database_name, database_type, species, timestamp, testcase, result, text) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
  );

  my $insert_annotation_sth = $dbi->prepare(
"INSERT INTO annotation (report_id, person, action, comment, created_at, modified_at, created_by, modified_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
  );

  my %counts;

  foreach my $type (@types) {

    print "Propagating $type\n" unless ($quiet);
    $counts{$type} = 0;

    # find all manual_ok_all_releases etc for each old database
    foreach my $old_database ( keys %$old_to_new_db_name ) {

      my $new_database = $old_to_new_db_name->{$old_database};

      my $count = 0;

      $select_sth->execute( $old_database, $type );
      foreach my $row ( @{ $select_sth->fetchall_arrayref() } ) {

        my (
          $first_session_id, $species, $database_type, $timestamp,
          $testcase,         $result,  $text,          $person,
          $action,           $comment, $created_at,    $modified_at,
          $created_by,       $modified_by
        ) = @$row;
        
        #If we have already processed this DB then we skip it
        if(has_been_propagated($new_database, $type)) {
          print "Skipping $new_database as it has already been propagated\n" if ! $quiet;
          next;
        }

        # type eq manual_ok_this_assembly:
        # will only need to propagate if it is the same assembly
        if ( $type eq 'manual_ok_this_assembly' ) {
          next if new_assembly( $old_database, $new_database );
        }

        # type eq manual_ok_this_genebuild
        # will only need to propagate if it is the same genebuild
        # i.e. if genebuild.start_date values in meta table are the same
        # so skip propagation if they are not the same
        if ( $type eq 'manual_ok_this_genebuild' ) {
          next if new_genebuild($old_database, $new_database);
        }
        
        # type eq manual_ok_this_regulatory_build:
        # will only need to propagate if it is the same regulatory build
        if($type eq 'manual_ok_this_regulatory_build') {
          next if new_regulatory_build($old_database, $new_database);
        }

        # create new report
        $insert_report_sth->execute(
          $first_session_id, $session_id,   $new_database,
          $database_type,    $species,      $timestamp,
          $testcase,         $result,       $text
        ) || die "Error inserting report";
        my $report_id = $insert_report_sth->{'mysql_insertid'};

        # create new annotation
        $insert_annotation_sth->execute(
          $report_id,  $person,      $action,     $comment,
          $created_at, $modified_at, $created_by, $modified_by
        ) || die "Error inserting annotation";
        $count++;
        $counts{$type}++;

      }
      $propagated_new_databases{$new_database} = 1;
      record_propagation($new_database, $type);

      print "\t$old_database\t$count\n" if ( !$quiet && $count > 0 );
    }

    print "Propagated " . $counts{$type} . " $type reports\n" unless ($quiet);

  }    # foreach type

  flag_non_propagated_dbs(\%propagated_new_databases);
  
  return;
}

# --------------------------------------------------------------------------------

# this method will return true if it is a new species assembly, false otherwise
# checks the contents of meta

sub new_assembly {
  my ($old_dbname, $new_dbname) = @_;
  my $old = $assembly_cache->{$old_dbname};
  my $new = $assembly_cache->{$new_dbname};
  return ("$old" eq "$new") ? 0 : 1; #force string comparison
}

# --------------------------------------------------------------------------------

# Will check if this is a new genebuild

sub new_genebuild {
  my ($old_dbname, $new_dbname) = @_;
  my $old = $meta_cache->{$old_dbname};
  my $new = $meta_cache->{$new_dbname};
  return ("$old" eq "$new") ? 0 : 1; #force string comparison
}

# --------------------------------------------------------------------------------

# Will check if this is a new regulatory build. New means either DB's build
# was null (as we can't check it) or the build values were not the same

sub new_regulatory_build {
  my ($old_dbname, $new_dbname) = @_;
  my $old = $regulatory_build_cache->{$old_dbname};
  my $new = $regulatory_build_cache->{$new_dbname};
  return 1 if ! defined $old || ! defined $new; 
  return 0 if $old eq $new;
  return 1;
}

# --------------------------------------------------------------------------------

# Combines the other two session methods to add to an existing session
# or create a new one if there is not one already available.

sub session_id {
  my ($dbi, $old_release, $new_release) = @_;
  my $last_session_id = get_latest_session_id($dbi, $new_release);
  return $last_session_id if $last_session_id >= 0;
  return create_session($old_release, $new_release);
} 

# --------------------------------------------------------------------------------

sub create_session {
  my ( $dbi, $old_release, $new_release ) = @_;
  my $sth = $dbi->prepare(
    "INSERT INTO session (db_release,config,start_time) VALUES (?,?,NOW())");
  $sth->execute( $new_release,
    "Propagation of entries from release $old_release to release $new_release" )
    || die "Error creating new session\n";
  my $new_session_id = $sth->{'mysql_insertid'};
  print "Created session ID $new_session_id for propagation\n" unless $quiet;
  return $new_session_id;
}

# --------------------------------------------------------------------------------

# method that will return the latest session_id in the database for the new_release

sub get_latest_session_id {
  my ($dbi, $new_release) = @_;
  #get latest session_id for the new release
  my $sql = 'SELECT max(session_id) FROM session WHERE db_release = ?';
  my $res = $dbi->selectcol_arrayref($sql, {}, $new_release);
  return $res->[0] if @{$res};
  return -1;
}

# --------------------------------------------------------------------------------
# Works with the propagation table which records what species have been seen 
# by this script meaning we can support multiple runs

sub record_propagation {
  my ($dbi, $dbname, $action) = @_;
  my %allowed = map { $_ => 1 } ('manual_ok_all_releases','manual_ok_this_assembly','manual_ok_this_genebuild','manual_ok_this_regulatory_build','none');
  die "Do not understand the action $action " unless $allowed{$action};
  my $sth = $dbi->prepare('insert into propagation (database_name, action, session_id) values (?,?,?)');
  $sth->execute($dbname, $session_id, $action); #session_id is a global
  $sth->finish();
  return;
}

# --------------------------------------------------------------------------------
# Queries the propagation table for the given database name and returns the 
# count of entries which map to this

sub has_been_propagated {
  my ($dbi, $dbname) = @_;
  my $sth = $dbi->prepare('select count(*) from propagation where database_name =?');
  $sth->execute($dbname);
  my ($count) = $sth->fetchrow_array();
  $sth->finish();
  return $count;
}

# --------------------------------------------------------------------------------
# Attempt to find all databases on the live release but did not have any data
# propagated. We still want to flag that we saw it but did nothing with it. We
# also need to check that we have not already propagated this data into the
# schema
sub flag_non_propagated_dbs {
  my ($propagated_new_databases) = @_;
  my %live_new_databases = map { $_ => 1 } @{get_new_databases($dbi1, $new_release)};
  if($dbi2) {
    %live_new_databases = map { $_ => 1 } @{get_new_databases($dbi2, $new_release)};
  }
  foreach my $new_database (keys %live_new_databases) {
    if(!$propagated_new_databases->{$new_database} && ! has_been_propagated($dbi1, $new_database)) {
      printf("Recording '%s' has been seen but we had no data to propagate\n", $new_database) if ! $quiet;
      record_propagation($new_database, 'none');
    }
  }
  return;
}

# --------------------------------------------------------------------------------
# Cache all the meta entries in all databases applicable
# Note all databases = all on $dbi_prev, $dbi1, $dbi2, e.g. all on ens-livemirror, ens-staging1, ens-staging2

sub create_meta_cache {
  my ( $dbi1, $dbi2, $dbi_prev ) = @_;
  my $die_if_none = 1;
  return build_meta_cache('core', 'genebuild.start_date', $die_if_none, $dbi1, $dbi2, $dbi_prev);
}

# --------------------------------------------------------------------------------
# Cache all the meta entries in all databases applicable
# Note all databases = all on $dbi_prev, $dbi1, $dbi2, e.g. all on ens-livemirror, ens-staging1, ens-staging2

sub create_assembly_cache {
  my ( $dbi1, $dbi2, $dbi_prev ) = @_;
  my $die_if_none = 1;
  return build_meta_cache('core', 'assembly.default', $die_if_none, $dbi1, $dbi2, $dbi_prev);
}

# --------------------------------------------------------------------------------
# Cache all the meta entries in all databases applicable
# Note all databases = all on $dbi_prev, $dbi1, $dbi2, e.g. all on ens-livemirror, ens-staging1, ens-staging2

sub create_regulation_cache {
  my ( $dbi1, $dbi2, $dbi_prev ) = @_;
  my $die_if_none = 0;
  return build_meta_cache('funcgen', 'regbuild.last_annotation_update', $die_if_none, $dbi1, $dbi2, $dbi_prev);
}

# --------------------------------------------------------------------------------
# Cache all the meta entries in all databases applicable using the specified keys

sub build_meta_cache {
  my ($dbtype, $meta_key, $die_if_none, @dbhs) = @_;
  print "Building cache '$meta_key' for '$dbtype'\n" unless $quiet;
  my %cache;
  foreach my $dbh (@dbhs) {
    my $like = "%\\_${dbtype}\\_%";
    my $dbs = $dbh->selectcol_arrayref('SHOW DATABASES LIKE ?', {}, $like);
    
    printf("Scanning through %d potential databases\n", scalar(@{$dbs})) unless $quiet;
    
    foreach my $db (@{$dbs}) {
      my $sql = sprintf('SELECT meta_value FROM %s.meta where meta_key =?', $dbname);
      my @row = $dbh->selectrow_array($sql, {}, $meta_key);
      my ($value) = @row;
      if($die_if_none && ! $value) {
        croak "Error getting $meta_key from $dbname. Program will abort";
      }
      $cache{$db} = $value if $value; 
    }
  }
  print "Finished cache building '$meta_key' for '$dbtype'\n" unless $quiet;
  return \%cache;
}

# --------------------------------------------------------------------------------

sub set_end_time {
  my ( $dbi ) = @_;

  my $sth =
    $dbi->prepare("UPDATE session SET end_time=NOW() WHERE session_id=?");
  $sth->execute($session_id) || die "Error setting end time\n";

  print "Set end time for session\n" unless $quiet;

}
