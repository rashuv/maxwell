package com.zendesk.maxwell;

import static org.junit.Assert.*;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;

import com.zendesk.maxwell.schema.Schema;
import com.zendesk.maxwell.schema.SchemaCapturer;
import com.zendesk.maxwell.schema.SchemaStore;
import com.zendesk.maxwell.schema.Table;
import com.zendesk.maxwell.schema.ddl.SchemaSyncError;

public class SchemaStoreTest extends AbstractMaxwellTest {
	private Schema schema;
	private BinlogPosition binlogPosition;
	private SchemaStore schemaStore;

	String schemaSQL[] = {
		"CREATE TABLE shard_1.latin1 (id int(11), str1 varchar(255), str2 varchar(255) character set 'utf8') charset = 'latin1'",
		"CREATE TABLE shard_1.enums (id int(11), enum_col enum('foo', 'bar', 'baz'))",
		"CREATE TABLE shard_1.pks (id int(11), col2 varchar(255), col3 datetime, PRIMARY KEY(col2, col3, id))"
	};

	@Before
	public void setUp() throws Exception {
		server.executeList(schemaSQL);
		this.schema = new SchemaCapturer(server.getConnection()).capture();
		this.binlogPosition = BinlogPosition.capture(server.getConnection());
		this.schemaStore = new SchemaStore(server.getConnection(), this.schema, binlogPosition);
	}

	@Test
	public void testSave() throws SQLException, IOException, SchemaSyncError {
		this.schemaStore.save();

		SchemaStore restoredSchema = SchemaStore.restore(server.getConnection(), binlogPosition);
		List<String> diff = this.schema.diff(restoredSchema.getSchema(), "captured schema", "restored schema");
		assertThat(StringUtils.join(diff, "\n"), diff.size(), is(0));
	}

	@Test
	public void testRestorePK() throws Exception {
		this.schemaStore.save();

		SchemaStore restoredSchema = SchemaStore.restore(server.getConnection(), binlogPosition);
		Table t = restoredSchema.getSchema().findDatabase("shard_1").findTable("pks");

		assertThat(t.getPKList(), is(not(nullValue())));
		assertThat(t.getPKList().size(), is(3));
		assertThat(t.getPKList().get(0), is("col2"));
		assertThat(t.getPKList().get(1), is("col3"));
		assertThat(t.getPKList().get(2), is("id"));
	}
}
