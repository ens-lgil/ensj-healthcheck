/*
 * Copyright [1999-2015] Wellcome Trust Sanger Institute and the
 * EMBL-European Bioinformatics Institute
 * Copyright [2016-2018] EMBL-European Bioinformatics Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ensembl.healthcheck.testcase.funcgen;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.Team;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

/**
 * Check that every alignment which has been used for peak calling
 * has an associated BIGWIG file entry stored in data_file table. Check
 * that the file actually exists on the disk.
 * @author ilavidas
 */

public class AlignmentHasBigWigFile extends DataFileTableHasFile {

    public AlignmentHasBigWigFile() {
        setTeamResponsible(Team.FUNCGEN);
        setDescription("Check that every alignment which has been used for " +
                "peak calling has an associated BIGWIG file entry stored in " +
                "data_file table. Check that the file actually exists on the " +
                "disk.");
    }

    @Override
    protected FileType getFileType() {
        return FileType.BIGWIG;
    }

    @Override
    protected TableName getTableName() {
        return TableName.alignment;
    }

    @Override
    HashMap<Integer, String> getTableIDs(DatabaseRegistryEntry dbre) {
        HashMap<Integer, String> tableIDs = new HashMap<Integer, String>();

        Connection con = dbre.getConnection();
        try {
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT select alignment.alignment_id, alignment.name from peak_calling join alignment on (peak_calling.signal_alignment_id = alignment.alignment_id or peak_calling.control_alignment_id = alignment.alignment_id) FROM alignment JOIN peak_calling USING (alignment_id)");

            while (rs != null && rs.next()) {
                tableIDs.put(rs.getInt(1), rs.getString(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return tableIDs;
    }

}
