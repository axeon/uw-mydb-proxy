package uw.mydb.parse;

import org.junit.Before;
import org.junit.Test;
import uw.mydb.sqlparse.SqlParseResult;
import uw.mydb.sqlparse.SqlParser;


/**
 * 解析器测试。
 */

public class ParserTest {

    /**
     * 初始化。
     */
    @Before
    public void init() {
        SqlTest.init();
    }

    @Test
    public void testInsert() {
        SqlParseResult result = new SqlParser( SqlTest.database, SqlTest.insert ).parse();
        System.out.println( result );
    }

    @Test
    public void testUpdate() {
        SqlParseResult result = new SqlParser( SqlTest.database, SqlTest.update ).parse();
        System.out.println( result );
    }

    @Test
    public void testSelect() {
        SqlParseResult result = new SqlParser( SqlTest.database, SqlTest.select ).parse();
        System.out.println( result );
    }

    @Test
    public void testExceptionSql() {
        String sql = "/* ApplicationName=DBeaver Ultimate 23.3.1 - Metadata */ SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='auth' AND TABLE_NAME='msc_perm' ORDER" +
                " BY ORDINAL_POSITION";
        SqlParseResult result = new SqlParser( (String) null, sql ).parse();
        System.out.println( result );
    }
}
