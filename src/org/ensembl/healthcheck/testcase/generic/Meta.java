/*
 * Copyright (C) 2004 EBI, GRL
 * 
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package org.ensembl.healthcheck.testcase.generic;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.DatabaseType;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.Species;
import org.ensembl.healthcheck.Team;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;
import org.ensembl.healthcheck.util.Utils;

/**
 * Check that the meta table exists and has data and the entries correspond to the database name and a few other basic data errors. 
 * Other meta_value tests are done in the MetaValue test case. 
 * Only one meta table at a time is done here; checks for the consistency of the
 * meta table across species are done in MetaCrossSpecies.
 */
public class Meta extends SingleDatabaseTestCase {
	private boolean isSangerVega = false;

	/**
	 * Creates a new instance of CheckMetaDataTableTestCase
	 */
	public Meta() {

		addToGroup("post_genebuild");
		addToGroup("release");
		addToGroup("compara-ancestral");
		addToGroup("pre-compara-handover");
		addToGroup("post-compara-handover");
		
		setTeamResponsible(Team.RELEASE_COORDINATOR);
		setSecondTeamResponsible(Team.GENEBUILD);
		setDescription("Check that the meta table exists and has data and the entries correspond to the database name");
	}

	/**
	 * Check that the meta table exists and has data and the entries correspond to the database name.
	 * 
	 * @param dbre
	 *          The database to check.
	 * @return True if the test passed.
	 */
	public boolean run(final DatabaseRegistryEntry dbre) {

		boolean result = true;

		Connection con = dbre.getConnection();

		Species species = dbre.getSpecies();

		result &= checkTableExists(con);

		result &= tableHasRows(con);

		result &= checkSchemaVersionDBName(dbre);

		if (species == Species.ANCESTRAL_SEQUENCES) {
			// The rest of the tests are not relevant for the ancestral sequences DB
			return result;
		}

		if (dbre.getType() == DatabaseType.CORE) {
			result &= checkKeysPresent(con);
			result &= checkKeysNotPresent(con);
		}

		// -------------------------------------------

		result &= checkDuplicates(dbre);

		// -------------------------------------------

		result &= checkArrays(dbre);

		
		return result;

	} // run

	// ---------------------------------------------------------------------

	private boolean checkTableExists(Connection con) {

		boolean result = true;

		if (!checkTableExists(con, "meta")) {
			result = false;
			ReportManager.problem(this, con, "Meta table not present");
		} else {
			ReportManager.correct(this, con, "Meta table present");
		}

		return result;

	}

	// ---------------------------------------------------------------------

	private boolean tableHasRows(Connection con) {

		boolean result = true;

		int rows = countRowsInTable(con, "meta");
		if (rows == 0) {
			result = false;
			ReportManager.problem(this, con, "meta table is empty");
		} else {
			ReportManager.correct(this, con, "meta table has data");
		}

		return result;

	}

	// ---------------------------------------------------------------------

	private boolean checkKeysPresent(Connection con) {

		boolean result = true;

		// check that certain keys exist
		String[] metaKeys = { "assembly.default", "species.classification", "species.ensembl_common_name", "species.taxonomy_id", "assembly.name", "assembly.date", "species.ensembl_alias_name",
				"repeat.analysis", "marker.priority", "assembly.coverage_depth", "species.stable_id_prefix", "species.production_name", "species.scientific_name", "species.short_name" };
		for (int i = 0; i < metaKeys.length; i++) {
			String metaKey = metaKeys[i];
			int rows = getRowCount(con, "SELECT COUNT(*) FROM meta WHERE meta_key='" + metaKey + "'");
			if (rows == 0) {
				result = false;
				ReportManager.problem(this, con, "No entry in meta table for " + metaKey);
			}
		}

		// check that there are some species.alias entries
		int MIN_ALIASES = 3;

		int rows = getRowCount(con, "SELECT COUNT(*) FROM meta WHERE meta_key='species.alias'");
		if (rows < MIN_ALIASES) {
			result = false;
			ReportManager.problem(this, con, "Only " + rows + " species.alias entries, should be at least " + MIN_ALIASES);
		} else {
			ReportManager.correct(this, con, rows + " species.alias entries present");
		}

		return result;
	}

	// ---------------------------------------------------------------------

	private boolean checkKeysNotPresent(Connection con) {

		boolean result = true;

		String[] metaKeys = {};
		for (int i = 0; i < metaKeys.length; i++) {
			String metaKey = metaKeys[i];
			int rows = getRowCount(con, "SELECT COUNT(*) FROM meta WHERE meta_key='" + metaKey + "'");
			if (rows > 0) {
				result = false;
				ReportManager.problem(this, con, rows + " meta entries for " + metaKey + " when there shouldn't be any");
			} else {
				ReportManager.correct(this, con, "No entry in meta table for " + metaKey + " - this is correct");
			}
		}

		return result;
	}


	// ---------------------------------------------------------------------
	/**
	 * Check that the schema_version in the meta table is present and matches the database name.
	 */
	private boolean checkSchemaVersionDBName(DatabaseRegistryEntry dbre) {

		boolean result = true;
		// get version from database name
		String dbNameVersion = dbre.getSchemaVersion();
		logger.finest("Schema version from database name: " + dbNameVersion);

		// get version from meta table
		Connection con = dbre.getConnection();

		if (dbNameVersion == null) {
			ReportManager.warning(this, con, "Can't deduce schema version from database name.");
			return false;
		}

		String schemaVersion = getRowColumnValue(con, "SELECT meta_value FROM meta WHERE meta_key='schema_version'");
		logger.finest("schema_version from meta table: " + schemaVersion);

		if (schemaVersion == null || schemaVersion.length() == 0) {

			ReportManager.problem(this, con, "No schema_version entry in meta table");
			return false;

		} else if (!schemaVersion.matches("[0-9]+")) {

			ReportManager.problem(this, con, "Meta schema_version " + schemaVersion + " is not numeric");
			return false;

		} else if (!dbNameVersion.equals(schemaVersion) && !isSangerVega) {// do not report for sanger_vega

			ReportManager.problem(this, con, "Meta schema_version " + schemaVersion + " does not match version inferred from database name (" + dbNameVersion + ")");
			return false;

		} else {

			ReportManager.correct(this, con, "schema_version " + schemaVersion + " matches database name version " + dbNameVersion);

		}
		return result;

	}

	// ---------------------------------------------------------------------
	/**
	 * Check that the assembly_version in the meta table is present and matches the database name.
	 */
	private boolean checkAssemblyVersion(Connection con, String dbNameAssemblyVersion, String metaTableAssemblyVersion) {

		boolean result = true;

		if (metaTableAssemblyVersion == null || metaTableAssemblyVersion.length() == 0) {

			ReportManager.problem(this, con, "No assembly_version entry in meta table");
			return false;

		} else if (!dbNameAssemblyVersion.equals(metaTableAssemblyVersion)) {

			ReportManager.problem(this, con, "Meta assembly_version " + metaTableAssemblyVersion + " does not match version inferred from database name (" + dbNameAssemblyVersion + ")");
			return false;

		} else {

			ReportManager.correct(this, con, "assembly_version " + metaTableAssemblyVersion + " matches database name version " + dbNameAssemblyVersion);

		}
		return result;

	}


	// ---------------------------------------------------------------------
	/**
	 * Check for duplicate entries in the meta table.
	 */
	private boolean checkDuplicates(DatabaseRegistryEntry dbre) {

		boolean result = true;

		Connection con = dbre.getConnection();

		try {

			Statement stmt = con.createStatement();

			ResultSet rs = stmt.executeQuery("SELECT meta_key, meta_value FROM meta GROUP BY meta_key, meta_value, species_id HAVING COUNT(*)>1");

			while (rs.next()) {

				ReportManager.problem(this, con, "Key/value pair " + rs.getString(1) + "/" + rs.getString(2) + " appears more than once in the meta table");
				result = false;

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		if (result) {
			ReportManager.correct(this, con, "No duplicates in the meta table");
		}

		return result;

	}

	// ---------------------------------------------------------------------
	/**
	 * Check for values containing the text ARRAY(.
	 */
	private boolean checkArrays(DatabaseRegistryEntry dbre) {

		boolean result = true;

		Connection con = dbre.getConnection();

		try {

			Statement stmt = con.createStatement();

			ResultSet rs = stmt.executeQuery("SELECT meta_key, meta_value FROM meta WHERE meta_value LIKE 'ARRAY(%'");

			while (rs.next()) {

				ReportManager.problem(this, con, "Meta table entry for key " + rs.getString(1) + " has value " + rs.getString(2) + " which is probably incorrect");
				result = false;

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		if (result) {
			ReportManager.correct(this, con, "No duplicates in the meta table");
		}

		return result;

	}


} // Meta
