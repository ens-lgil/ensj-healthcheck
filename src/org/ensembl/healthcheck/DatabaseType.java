/*
 Copyright (C) 2003 EBI, GRL
 
 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.
 
 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.
 
 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.ensembl.healthcheck;


/**
 * Typesafe "enum" to store information about the type of a database. Declared final since it only
 * has private constructors.
 */
public final class DatabaseType {

    /** A core database */
    public static final DatabaseType CORE = new DatabaseType("core");

    /** An EST database */
    public static final DatabaseType EST = new DatabaseType("est");

    /** An ESTgene database */
    public static final DatabaseType ESTGENE = new DatabaseType("estgene");

    /** A Vega database */
    public static final DatabaseType VEGA = new DatabaseType("vega");

    /** A Compara database */
    public static final DatabaseType COMPARA = new DatabaseType("compara");

    /** A Mart database */
    public static final DatabaseType MART = new DatabaseType("mart");

    /** A variation database */
    public static final DatabaseType VARIATION = new DatabaseType("variation");

    /** A disease database */
    public static final DatabaseType DISEASE = new DatabaseType("disease");

    /** A haplotype database */
    public static final DatabaseType HAPLOTYPE = new DatabaseType("haplotype");

    /** A lite database */
    public static final DatabaseType LITE = new DatabaseType("lite");

    /** A GO database */
    public static final DatabaseType GO = new DatabaseType("go");
    
    /** An expression database */
    public static final DatabaseType EXPRESSION = new DatabaseType("expression");
    
    /** An xref database */
    public static final DatabaseType XREF = new DatabaseType("xref");
    
    /** An cDNA database */
    public static final DatabaseType CDNA = new DatabaseType("cdna");

    /** A database whos type has not been determined */
    public static final DatabaseType UNKNOWN = new DatabaseType("unknown");

    private final String name;

    private DatabaseType(final String name) {

        this.name = name;
    }

    /**
     * @return a String representation of this DatabaseType object.
     */
    public String toString() {

        return this.name;
    }

    // -----------------------------------------------------------------
    /**
     * Resolve an alias to a DatabaseType object.
     * 
     * @param alias The alias (e.g. core).
     * @return The DatabaseType object corresponding to alias, or DatabaseType.UNKNOWN if it cannot
     *         be resolved.
     */
    public static DatabaseType resolveAlias(final String alias) {

        String lcAlias = alias.toLowerCase();

	// --------------------------------------
	// needs to be before core and est since names 
	// are of the form homo_sapiens_core_expression_est_24_34e
        if (in(lcAlias, "expression")) { 

        return EXPRESSION; 

        }

        // --------------------------------------

        if (in(lcAlias, "core")) { 

        return CORE; 

        }

        // --------------------------------------

        if (in(lcAlias, "est")) { 

        return EST; 

        }

        // --------------------------------------

        if (in(lcAlias, "estgene")) { 

        return ESTGENE; 

        }

        // --------------------------------------

        if (in(lcAlias, "compara")) { 

        return COMPARA; 

        }

        // --------------------------------------

        if (in(lcAlias, "mart")) { 

        return MART; 

        }

        // --------------------------------------

        if (in(lcAlias, "vega")) { 

        return VEGA; 

        }

        // --------------------------------------

        if (in(lcAlias, "variation")) { 

        return VARIATION; 

        }

        // --------------------------------------

        if (in(lcAlias, "disease")) { 

        return DISEASE; 

        }

        // --------------------------------------

        if (in(lcAlias, "haplotype")) { 

        return HAPLOTYPE; 

        }

        // --------------------------------------

        if (in(lcAlias, "lite")) { 

        return LITE; 

        }
        
        // --------------------------------------

        if (in(lcAlias, "go")) { 

        return GO; 

        }

        // --------------------------------------
	
	if (in(lcAlias, "expression")) { 

	    return EXPRESSION; 

        }

	// --------------------------------------
	
	if (in(lcAlias, "xref")) { 

	    return XREF; 

        }


	// --------------------------------------
	
	if (in(lcAlias, "cdna")) { 

	    return CDNA; 

        }

	// --------------------------------------

        // default case
        return UNKNOWN;

    } // resolveAlias

    // -----------------------------------------------------------------

    /**
     * Return true if alias appears somewhere in comma-separated list.
     */
    private static boolean in(final String alias, final String list) {

        return (list.indexOf(alias) > -1);

    }
    
    // -------------------------------------------------------------------------
    /**
     * Check if a DatabaseType is generic (core, est, estgene, vega).
     * @return true if t is core, est, estgene or vega.
     */
    public boolean isGeneric() {
        
        if (name.equals("core") || name.equals("est") || name.equals("estgene") || name.equals("vega") || name.equals("cdna")) {
            return true;
        }
        
        return false;
        
    }
    
    // -----------------------------------------------------------------

} // DatabaseType
