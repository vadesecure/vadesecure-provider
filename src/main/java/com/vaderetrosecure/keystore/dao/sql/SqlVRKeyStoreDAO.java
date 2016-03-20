/**
 * 
 */
package com.vaderetrosecure.keystore.dao.sql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.log4j.Logger;

import com.vaderetrosecure.keystore.dao.KeyStoreEntry;
import com.vaderetrosecure.keystore.dao.KeyStoreEntryType;
import com.vaderetrosecure.keystore.dao.KeyStoreMetaData;
import com.vaderetrosecure.keystore.dao.KeyStoreDAO;
import com.vaderetrosecure.keystore.dao.KeyStoreDAOException;

/**
 * @author ahonore
 *
 */
class SqlVRKeyStoreDAO implements KeyStoreDAO
{
    private static final Logger LOG = Logger.getLogger(SqlVRKeyStoreDAO.class);
    private static final String KEYSTORE_ENTRIES_TABLE = "keystore_entries";
    private static final String KEYSTORE_METADATA_TABLE = "keystore_metadata";
    
    private DataSource dataSource;
    
    SqlVRKeyStoreDAO(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    @Override
    public void createSchema() throws KeyStoreDAOException
    {
        try (Connection conn = dataSource.getConnection())
        {
            boolean autoCom = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("schema.sql"))))
            {
                String line;
                while ((line = br.readLine()) != null)
                {
                    line = line.trim();
                    if (line.isEmpty())
                        continue;
                    
                    try (PreparedStatement ps = conn.prepareStatement(line))
                    {
                        ps.executeUpdate();
                    }
                }
            }
            
            conn.commit();
            conn.setAutoCommit(autoCom);
        }
        catch (SQLException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
        catch (IOException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
    }

    @Override
    public KeyStoreMetaData getMetaData() throws KeyStoreDAOException
    {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("select * from " + KEYSTORE_METADATA_TABLE + " limit 1"); ResultSet rs = ps.executeQuery())
        {
            if (!rs.next())
                return null;

            KeyStoreMetaData ksmd = new KeyStoreMetaData();
            ksmd.setMajorVersion(rs.getInt("major_version"));
            ksmd.setVersion(rs.getString("version"));
            ksmd.setSalt(EncodingTools.b64Decode(rs.getString("salt")));
            ksmd.setIV(EncodingTools.b64Decode(rs.getString("iv")));
            ksmd.setKeyIV(EncodingTools.b64Decode(rs.getString("key_iv")));
            ksmd.setKeyIVHash(EncodingTools.hexStringDecode(rs.getString("key_iv_hash")));
            return ksmd;
        }
        catch (SQLException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
    }

    @Override
    public int countEntries() throws KeyStoreDAOException
    {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("select distinct count(alias) from " + KEYSTORE_ENTRIES_TABLE);
                ResultSet rs = ps.executeQuery())
        {
            if (!rs.next())
                return 0;
            
            return rs.getInt(1);
        }
        catch (SQLException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
    }

    @Override
    public List<String> getAliases() throws KeyStoreDAOException
    {
        List<String> aliases = new ArrayList<>();
        try (Connection conn = dataSource.getConnection(); 
                PreparedStatement ps = conn.prepareStatement("select distinct alias from " + KEYSTORE_ENTRIES_TABLE); 
                ResultSet rs = ps.executeQuery())
        {
            while (rs.next())
                aliases.add(rs.getString(1));
            
            return aliases;
        }
        catch (SQLException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
    }

    @Override
    public List<KeyStoreEntry> getKeyStoreEntry(String alias, KeyStoreEntryType keyStoreEntryType) throws KeyStoreDAOException
    {
        List<KeyStoreEntry> entries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("select * from " + KEYSTORE_ENTRIES_TABLE + " where alias=? and entry_type=?"))
        {
            ps.setString(1, alias);
            ps.setInt(2, keyStoreEntryType.ordinal());
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                    entries.add(getKeyStoreEntryObject(rs));
            }
            return entries;
        }
        catch (SQLException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
    }

    @Override
    public List<KeyStoreEntry> getKeyStoreEntry(String alias) throws KeyStoreDAOException
    {
        List<KeyStoreEntry> entries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("select * from " + KEYSTORE_ENTRIES_TABLE + " where alias=?"))
        {
            ps.setString(1, alias);
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                    entries.add(getKeyStoreEntryObject(rs));
            }
            return entries;
        }
        catch (SQLException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
    }

    @Override
    public Date engineGetCreationDate(String alias) throws KeyStoreDAOException
    {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("select creation_date from " + KEYSTORE_ENTRIES_TABLE + " where alias=? limit 1"))
        {
            ps.setString(1, alias);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    return Date.from(Instant.ofEpochMilli(rs.getLong(1)));
                }
            }
            return null;
        }
        catch (SQLException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
    }

    @Override
    public void setMetaData(KeyStoreMetaData keyStoreMetaData) throws KeyStoreDAOException
    {
        // delete if exists then insert
        try (Connection conn = dataSource.getConnection())
        {
            boolean autoCom = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement("delete from " + KEYSTORE_METADATA_TABLE + " where major_version=?"))
            {
                ps.setInt(1, keyStoreMetaData.getMajorVersion());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("insert into " + KEYSTORE_METADATA_TABLE + " (major_version,version,salt,iv,key_iv,key_iv_hash) value(?,?,?,?,?,?)"))
            {
                ps.setInt(1, keyStoreMetaData.getMajorVersion());
                ps.setString(2, keyStoreMetaData.getVersion());
                ps.setString(3, EncodingTools.b64Encode(keyStoreMetaData.getSalt()));
                ps.setString(4, EncodingTools.b64Encode(keyStoreMetaData.getIV()));
                ps.setString(5, EncodingTools.b64Encode(keyStoreMetaData.getKeyIV()));
                ps.setString(6, EncodingTools.hexStringEncode(keyStoreMetaData.getKeyIVHash()));
                ps.executeUpdate();
            }

            conn.commit();
            conn.setAutoCommit(autoCom);
        }
        catch (SQLException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
    }

    @Override
    public void setKeyStoreEntries(Collection<KeyStoreEntry> keyStoreEntries) throws KeyStoreDAOException
    {
        try (Connection conn = dataSource.getConnection())
        {
            boolean autoCom = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement psDel = conn.prepareStatement("delete from " + KEYSTORE_ENTRIES_TABLE + " where alias=?"); 
            		PreparedStatement psIns = conn.prepareStatement("insert into " + KEYSTORE_ENTRIES_TABLE + " (alias,entry_type,rank,creation_date,algorithm,data) value(?,?,?,?,?,?)"))
            {
	            for (KeyStoreEntry kse : keyStoreEntries)
	            {
	            	psDel.setString(1, kse.getAlias());
	            	psDel.executeUpdate();

	            	psIns.setString(1, kse.getAlias());
	            	psIns.setInt(2, kse.getEntryType().ordinal());
	            	psIns.setInt(3, kse.getRank());
	            	psIns.setLong(4, kse.getCreationDate().getTime());
	            	psIns.setString(5, kse.getAlgorithm());
	            	psIns.setString(6, EncodingTools.b64Encode(kse.getData()));
	            	psIns.executeUpdate();
	            }
            }
            
            conn.commit();
            conn.setAutoCommit(autoCom);
        }
        catch (SQLException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
    }

    @Override
    public void deleteKeyStoreEntry(String alias) throws KeyStoreDAOException
    {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("delete from " + KEYSTORE_ENTRIES_TABLE + " where alias=?"))
        {
        	ps.setString(1, alias);
        	ps.executeUpdate();
        }
        catch (SQLException e)
        {
            LOG.error(e, e);
            throw new KeyStoreDAOException(e);
        }
    }
    
    private KeyStoreEntry getKeyStoreEntryObject(ResultSet resultSet) throws SQLException
    {
        KeyStoreEntry kse = new KeyStoreEntry();
        kse.setAlias(resultSet.getString("alias"));
        kse.setEntryType(KeyStoreEntryType.values()[resultSet.getInt("entry_type")]);
        kse.setRank(resultSet.getInt("rank"));
        kse.setCreationDate(Date.from(Instant.ofEpochMilli(resultSet.getLong("creation_date"))));
        kse.setAlgorithm(resultSet.getString("algorithm"));
        kse.setData(EncodingTools.b64Decode(resultSet.getString("data")));
        return kse;
    }
}
