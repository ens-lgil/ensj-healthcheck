/*
 * Copyright (C) 2004 EBI, GRL
 * 
 * This library is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with
 * this library; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */

package org.ensembl.healthcheck.testcase.generic;

import java.sql.*;
import java.util.regex.*;

import org.ensembl.healthcheck.testcase.*;
import org.ensembl.healthcheck.util.*;
import org.ensembl.healthcheck.*;

/**
 * Checks the metadata table to make sure it is OK. Only one meta table at a time is done
 * here; checks for the consistency of the meta table across species are done in
 * MetaCrossSpecies.
 */
public class Meta extends SingleDatabaseTestCase {

	// update this array as necessary
	private static final String[] validPrefixes = { "RGSC", "DROM", "ZFISH", "FUGU", "MOZ", "CEL", "CBR", "MGSC", "NCBI", "NCBIM" };

	/**
	 * Creates a new instance of CheckMetaDataTableTestCase
	 */
	public Meta() {
		addToGroup("post_genebuild");
		addToGroup("release");
		setDescription(
			"Check that the meta table exists, has data, the entries correspond to the "
				+ "database name, and that the values in assembly.type match what's in the meta table");
	}

	/**
	 * Check various aspects of the meta table.
	 * 
	 * @return Result.
	 */
	public boolean run(DatabaseRegistryEntry dbre) {

		boolean result = true;

			Connection con = dbre.getConnection();

			String dbName = DBUtils.getShortDatabaseName(con);

			// ----------------------------------------
			// Check that the meta table exists
			if (!checkTableExists(con, "meta")) {
				result = false;
				//logger.severe(dbName + " does not have a meta table!");
				//xxReportManager.problem(this, con, "Meta table not present");
			} else {
				//xxReportManager.correct(this, con, "Meta table present");
			}

			// ----------------------------------------
			// check meta table has > 0 rows
			int rows = countRowsInTable(con, "meta");
			if (rows == 0) {
				result = false;
				//warn(con, " has empty meta table");
				ReportManager.problem(this, con, "meta table is empty");
			} else {
				ReportManager.correct(this, con, "meta table has data");
			}

			// ----------------------------------------
			// check that there are species, classification and taxonomy_id entries
			String[] meta_keys = { "assembly.default", "species.classification", "species.common_name", "species.taxonomy_id" };
			for (int i = 0; i < meta_keys.length; i++) {
				String meta_key = meta_keys[i];
				rows = getRowCount(con, "SELECT COUNT(*) FROM meta WHERE meta_key='" + meta_key + "'");
				if (rows == 0) {
					result = false;
					//warn(con, "No entry in meta table for " + meta_key);
					ReportManager.problem(this, con, "No entry in meta table for " + meta_key);
				} else {
					ReportManager.correct(this, con, meta_key + " entry present");
				}
			}
			// ----------------------------------------
			// Use an AssemblyNameInfo object to get te assembly information
			AssemblyNameInfo assembly = new AssemblyNameInfo(con);

			String metaTableAssemblyDefault = assembly.getMetaTableAssemblyDefault();
			logger.finest("assembly.default from meta table: " + metaTableAssemblyDefault);
			String dbNameAssemblyVersion = assembly.getDBNameAssemblyVersion();
			logger.finest("Assembly version from DB name: " + dbNameAssemblyVersion);
			String metaTableAssemblyVersion = assembly.getMetaTableAssemblyVersion();
			logger.finest("meta table assembly version: " + metaTableAssemblyVersion);
			String metaTableAssemblyPrefix = assembly.getMetaTableAssemblyPrefix();
			logger.finest("meta table assembly prefix: " + metaTableAssemblyPrefix);

			if (metaTableAssemblyVersion == null
				|| metaTableAssemblyDefault == null
				|| metaTableAssemblyPrefix == null
				|| dbNameAssemblyVersion == null) {

					ReportManager.problem(this, con, "Cannot get all information from meta table - check for null values");

			} else {

				if (!metaTableAssemblyVersion.equalsIgnoreCase(dbNameAssemblyVersion)) {
					result = false;
					//warn(con, "Database name assembly version (" + dbNameAssemblyVersion + ")
					// does not match meta table assembly version (" + metaTableAssemblyVersion +
					// ").");
					ReportManager.problem(
						this,
						con,
						"Database name assembly version ("
							+ dbNameAssemblyVersion
							+ ") does not match meta table assembly version ("
							+ metaTableAssemblyVersion
							+ ")");
				} else {
					ReportManager.correct(this, con, "Assembly version in database name matches assembly version in meta table");
				}

				// ----------------------------------------
				// Check that assembly prefix is one of the correct ones
				boolean member = false;
				for (int i = 0; i < validPrefixes.length; i++) {
					if (metaTableAssemblyPrefix.equalsIgnoreCase(validPrefixes[i])) {
						member = true;
					}
				}
				if (!member) {
					result = false;
					//warn(con, "Assembly prefix (" + metaTableAssemblyPrefix + ") is not valid");
					ReportManager.problem(this, con, "Assembly prefix (" + metaTableAssemblyPrefix + ") is not valid");
				} else {
					ReportManager.correct(this, con, "Meta table assembly prefix (" + metaTableAssemblyPrefix + ") is valid");
				}
			}
			// ----------------------------------------
			// Check that species.classification matches database name

			String[] metaTableSpeciesGenusArray =
				getColumnValues(con, "SELECT LCASE(meta_value) FROM meta WHERE meta_key='species.classification' ORDER BY meta_id LIMIT 2");
			// if all is well, metaTableSpeciesGenusArray should contain the species and genus
			// (in that order) from the meta table

			if (metaTableSpeciesGenusArray != null
				&& metaTableSpeciesGenusArray.length == 2
				&& metaTableSpeciesGenusArray[0] != null
				&& metaTableSpeciesGenusArray[1] != null) {

				String[] dbNameGenusSpeciesArray = dbName.split("_");
				String dbNameGenusSpecies = dbNameGenusSpeciesArray[0] + "_" + dbNameGenusSpeciesArray[1];
				String metaTableGenusSpecies = metaTableSpeciesGenusArray[1] + "_" + metaTableSpeciesGenusArray[0];
				logger.finest("Classification from DB name:" + dbNameGenusSpecies + " Meta table: " + metaTableGenusSpecies);
				if (!dbNameGenusSpecies.equalsIgnoreCase(metaTableGenusSpecies)) {
					result = false;
					//warn(con, "Database name does not correspond to species/genus data from meta
					// table");
					ReportManager.problem(this, con, "Database name does not correspond to species/genus data from meta table");
				} else {
					ReportManager.correct(this, con, "Database name corresponds to species/genus data from meta table");
				}

			} else {
				//logger.warning("Cannot get species information from meta table");
				ReportManager.problem(this, con, "Cannot get species information from meta table");
			}

			// -------------------------------------------
			// Check formatting of assembly.mapping entries
			// should be of format
			// coord_system1{:default}|coord_system2{:default} with optional third coordinate
			// system
			// and all coord systems should be valid from coord_system
			Pattern assemblyMappingPattern = Pattern.compile("^(\\w+)(:\\w+)?\\|(\\w+)(:\\w+)?(\\|(\\w+)(:\\w+)?)?$");
			String[] validCoordSystems = getColumnValues(con, "SELECT name FROM coord_system");

			String[] mappings = getColumnValues(con, "SELECT meta_value FROM meta WHERE meta_key='assembly.mapping'");
			for (int i = 0; i < mappings.length; i++) {
				Matcher matcher = assemblyMappingPattern.matcher(mappings[i]);
				if (!matcher.matches()) {
					result = false;
					ReportManager.problem(this, con, "Coordinate system mapping " + mappings[i] + " is not in the correct format");
				} else {
					// if format is OK, check coord systems are valid
					boolean valid = true;
					String cs1 = matcher.group(1);
					String cs2 = matcher.group(3);
					String cs3 = matcher.group(6);
					if (!Utils.stringInArray(cs1, validCoordSystems, false)) {
						valid = false;
						ReportManager.problem(this, con, "Source co-ordinate system " + cs1 + " is not in the coord_system table");
					}
					if (!Utils.stringInArray(cs2, validCoordSystems, false)) {
						valid = false;
						ReportManager.problem(this, con, "Target co-ordinate system " + cs2 + " is not in the coord_system table");
					}
					if (cs3 != null && !Utils.stringInArray(cs3, validCoordSystems, false)) { // third
																																										// CS
																																										// is
																																										// optional
						valid = false;
						ReportManager.problem(this, con, "Third co-ordinate system in mapping (" + cs3 + ") is not in the coord_system table");
					}
					if (valid == true) {
						ReportManager.correct(this, con, "Coordinate system mapping " + mappings[i] + " is OK");
					}
				}
			}

			// -------------------------------------------
			// Check that the taxonomy ID matches a known one.
			// The taxonomy ID-species mapping is held in the Species class.
			
			Species species = dbre.getSpecies();
			String dbTaxonID = getRowColumnValue(con, "SELECT meta_value FROM meta WHERE meta_key='species.taxonomy_id'");
			logger.finest("Taxonomy ID from database: " + dbTaxonID);
			
			if (dbTaxonID.equals(Species.getTaxonomyID(species))) {
				ReportManager.correct(this, con, "Taxonomy ID " + dbTaxonID + " is correct for " + species.toString());
			} else {
				result = false;
				ReportManager.problem(this, con, "Taxonomy ID " + dbTaxonID + " in database is not correct - should be " + Species.getTaxonomyID(species) + " for " + species.toString());
			}
			// -------------------------------------------

		return result;

	} // run

} // Meta
