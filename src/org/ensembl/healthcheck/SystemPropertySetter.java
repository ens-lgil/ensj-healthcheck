package org.ensembl.healthcheck;

import org.ensembl.healthcheck.configuration.ConfigurationUserParameters;
import org.ensembl.healthcheck.util.SqlTemplate;

import uk.co.flamingpenguin.jewel.cli.Option;

/**
 * Copies certain configuration properties into system properties. They are 
 * used by some healthchecks, but also by modules like the {@link ReportManager}.
 * 
 * {@link org.ensembl.healthcheck.util.DBUtils} used to use system properties 
 * as well, but has been refactored to take in a configuration object and use 
 * that instead.
 *
 */
public class SystemPropertySetter {

	protected final ConfigurationUserParameters configuration;
	
	public SystemPropertySetter(ConfigurationUserParameters configuration) {
		this.configuration = configuration;
	}

	public void setPropertiesForReportManager_createDatabaseSession() {

		System.setProperty("output.password",    configuration.getOutputPassword());
		System.setProperty("host",           configuration.getHost() );
		System.setProperty("port",           configuration.getPort() );
		System.setProperty("output.release", configuration.getOutputRelease() );			

	}

	public void setPropertiesForReportManager_connectToOutputDatabase() {
		
		System.setProperty("output.driver",      configuration.getDriver());
		System.setProperty(
			"output.databaseURL", 
			"jdbc:mysql://"
				+ configuration.getOutputHost()
				+ ":"
				+ configuration.getOutputPort()
				+ "/"
		);
		
		System.setProperty("output.database",    configuration.getOutputDatabase());
		System.setProperty("output.user",        configuration.getOutputUser());
		System.setProperty("output.password",    configuration.getOutputPassword());
	}
	
	/**
	 * Sets system properties for the healthchecks.
	 * 
	 */
	public void setPropertiesForHealthchecks() {
		
		if (configuration.isBiotypesFile()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.generic.Biotypes
			//
			System.setProperty("biotypes.file",    configuration.getBiotypesFile());
		}

		if (configuration.isIgnorePreviousChecks()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.generic.ComparePreviousVersionExonCoords
			// org.ensembl.healthcheck.testcase.generic.ComparePreviousVersionBase
			// org.ensembl.healthcheck.testcase.generic.GeneStatus
			//
			System.setProperty("ignore.previous.checks",    configuration.getIgnorePreviousChecks());
		}

		if (configuration.isSchemaFile()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.generic.CompareSchema
			// org.ensembl.healthcheck.testcase.variation.CompareVariationSchema
			// org.ensembl.healthcheck.testcase.funcgen.CompareFuncgenSchema
			//		
			System.setProperty("schema.file",    configuration.getSchemaFile());
		}
		
		if (configuration.isMasterSchema()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.generic.CompareSchema
			// org.ensembl.healthcheck.testcase.funcgen.CompareFuncgenSchema
			//
			System.setProperty("master.schema",    configuration.getMasterSchema());
		}
		
		if (configuration.isLogicnamesFile()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.generic.LogicNamesDisplayable
			//		
			System.setProperty("logicnames.file",    configuration.getLogicnamesFile());
		}
		
		if (configuration.isPerl()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.AbstractPerlBasedTestCase
			//	
			System.setProperty(
				org.ensembl.healthcheck.testcase.AbstractPerlBasedTestCase.PERL, 
				configuration.getPerl()
			);
		}
		
		if (configuration.isMasterVariationSchema()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.variation.CompareVariationSchema
			//	
			System.setProperty("master.variation_schema",    configuration.getMasterVariationSchema());
		}
		
		if (configuration.isUserDir()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.EnsTestCase
			//
			System.setProperty("user.dir",    configuration.getUserDir());
		}
		
		if (configuration.isFileSeparator()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.EnsTestCase
			//
			System.setProperty("file.separator",    configuration.getFileSeparator());
		}
		
		if (configuration.isDriver()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.EnsTestCase
			//
			System.setProperty("driver",    configuration.getDriver());
		}
		
		if (configuration.isDatabaseURL()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.EnsTestCase
			//
			System.setProperty("databaseURL",    configuration.getDatabaseURL());
		}
		
		if (configuration.isUser()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.EnsTestCase
			//
			System.setProperty("user",    configuration.getUser());
		}
		
		if (configuration.isPassword()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.EnsTestCase
			//
			System.setProperty("password",    configuration.getPassword());
		}
		
		if (configuration.isMasterFuncgenSchema()) {
			// Used in:
			//
			// org.ensembl.healthcheck.testcase.funcgen.CompareFuncgenSchema
			//
			System.setProperty("master.funcgen_schema",    configuration.getMasterFuncgenSchema());
		}
	}
}