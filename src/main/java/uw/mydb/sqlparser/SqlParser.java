package uw.mydb.sqlparser;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.conf.MydbConfigService;
import uw.mydb.constant.MydbRouteMatchMode;
import uw.mydb.protocol.constant.MySqlErrorCode;
import uw.mydb.proxy.ProxySession;
import uw.mydb.route.RouteAlgorithm;
import uw.mydb.route.RouteManager;
import uw.mydb.sqlparser.parser.HintTypes;
import uw.mydb.sqlparser.parser.Lexer;
import uw.mydb.sqlparser.parser.Token;
import uw.mydb.vo.DataNode;
import uw.mydb.vo.DataTable;
import uw.mydb.vo.TableConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static uw.mydb.sqlparser.parser.Token.*;

/**
 * Sql解析器，根据table sharding设置决定分发mysql。
 * 对于常见的select,insert,update,delete等语句会解析sql表信息。
 * 对于其它的sql，需要hint注解指定运行目标，否则仅在base node上执行。
 *
 * @author axeon
 */
public class SqlParser {

    private static final Logger log = LoggerFactory.getLogger( SqlParser.class );

    /**
     * 代理session
     */
    private ProxySession proxySession;

    /**
     * lexer解析器。
     */
    private Lexer lexer;

    /**
     * lexer解析位置，用于sql分割。
     */
    private int lexerPos = 0;

    /**
     * hint的Route信息。
     */
    private String hintRouteInfo = null;

    /**
     * 被分割的子sql，用于快速sql，减少替换的IO操作。
     */
    private List<String> subSqlList = new ArrayList<>();

    /**
     * 整个SQL关联的routeKeyData
     */
    private RouteAlgorithm.RouteKeyData routeKeyData = new RouteAlgorithm.RouteKeyData();

    /**
     * 主路由信息。
     */
    private TableRouteData mainTableRouteData;


    /**
     * 路由信息列表，除了主路由之外的子表路由。
     */
    private List<TableRouteData> tableRouteDataList;


    /**
     * sql解析结果。
     */
    private SqlParseResult parseResult;


    /**
     * 单sql结果。
     */
    private SqlParseResult.SqlInfo sqlInfo;

    /**
     * 多sql结果。
     */
    private List<SqlParseResult.SqlInfo> sqlInfoList = null;

    /**
     * 默认构造器。
     *
     * @param proxySession
     * @param sqlSource
     */
    public SqlParser(ProxySession proxySession, String sqlSource) {
        this.proxySession = proxySession;
        this.lexer = new Lexer( sqlSource, false, true );
        this.parseResult = new SqlParseResult( proxySession.getDatabase(), sqlSource );
    }

    /**
     * 默认构造器。
     *
     * @param databaseSource
     * @param sqlSource
     */
    public SqlParser(String databaseSource, String sqlSource) {
        this.lexer = new Lexer( sqlSource, false, true );
        this.parseResult = new SqlParseResult( databaseSource, sqlSource );
    }

    /**
     * 解析sql。
     */
    public SqlParseResult parse() {
        if (!lexer.isEOF()) {
            lexer.nextToken();
            //处理注解
            if (lexer.token() == HINT) {
                parseHint( lexer );
                lexer.nextToken();
            }
            //跳过注释。
            for (int i = 0; i < 999; i++) {
                if (lexer.token() == COMMENT || lexer.token() == LINE_COMMENT || lexer.token() == MULTI_LINE_COMMENT) {
                    lexer.nextToken();
                } else {
                    break;
                }
            }
            switch (lexer.token()) {
                case SELECT:
                    this.parseResult.setMasterIfNull( false );
                    parseSelect( lexer );
                    break;
                case INSERT:
                    parseInsert( lexer );
                    break;
                case UPDATE:
                    parseUpdate( lexer );
                    break;
                case DELETE:
                    parseDelete( lexer );
                    break;
                case REPLACE:
                    parseReplace( lexer );
                    break;
                case CREATE:
                    this.parseResult.setDML( false );
                    parseCreate( lexer );
                    break;
                case ALTER:
                    this.parseResult.setDML( false );
                    parseAlter( lexer );
                    break;
                case DROP:
                    this.parseResult.setDML( false );
                    parseDrop( lexer );
                    break;
                case TRUNCATE:
                    this.parseResult.setDML( false );
                    parseTruncate( lexer );
                    break;
                case EXPLAIN:
                    this.parseResult.setDML( false );
                    parseExplain( lexer );
                    break;
                case USE:
                    parseUse( lexer );
                    break;
                default:
                    if (lexer.token() == SET || lexer.token() == SHOW || lexer.token() == EXPLAIN || lexer.token() == DESCRIBE || lexer.token() == EOF) {
                        //有些类型需要通过虚拟schema上支持的,，这些类型必须可以过。
                    } else {
                        //此时应该是不可用，抛错吧。
                        //剩下的类型都不支持，直接返回报错吧。
                        parseResult.setErrorInfo( MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORTED CMD: " + parseResult.getSourceSql() );
                    }
                    break;
            }
        }
        //设置table
        if (this.mainTableRouteData != null && this.mainTableRouteData.tableConfig != null) {
            this.parseResult.setSourceTable( this.mainTableRouteData.tableConfig.getTableName() );
        }

        if (!parseResult.hasError()) {
            //如果master未设置，处于保险，设置为true
            this.parseResult.setMasterIfNull( true );
            //计算路由信息。
            calculateAllRouteInfo();
        }

        if (!parseResult.hasError()) {
            //生成sql。
            generateSqlInfo();
        }

        return parseResult;
    }

    /**
     * 解析use语句。
     *
     * @param lexer
     */
    private void parseUse(Lexer lexer) {
        lexer.check( Token.USE );
        lexer.nextToken();
        if (lexer.token() == Token.IDENTIFIER) {
            //表名
            String database = lexer.stringVal();
            if (proxySession != null) {
                proxySession.setDatabase( database );
            }
        }
        //通过设置parseResult，不让cmdQuery再返回数据。
        this.parseResult.setErrorInfo( -1, null );
    }


    /**
     * 解析mydb专有hint。
     *
     * @param lexer
     */
    private void parseHint(Lexer lexer) {
        lexer.check( Token.HINT );
        String hint = lexer.stringVal();
        if (hint.startsWith( HintTypes.MYDB_HINT )) {
            int mark = HintTypes.MYDB_HINT.length();
            int pos = mark;
            //循环解析。
            while (pos < hint.length()) {
                pos = hint.indexOf( '=', mark );
                if (pos == -1) {
                    break;
                }
                //属性值
                String name = hint.substring( mark, pos ).trim();
                //寻找数值结尾。
                mark = hint.indexOf( ';' );
                if (mark == -1) {
                    //说明已经到结尾了
                    mark = hint.length();
                }
                String value = hint.substring( pos + 1, mark ).trim();
                if (HintTypes.DB_TYPE.equalsIgnoreCase( name )) {
                    //此处处理balance类型。
                    if (HintTypes.DB_TYPE_MASTER.equalsIgnoreCase( value )) {
                        parseResult.setMasterQuery( true );
                    } else {
                        parseResult.setMasterQuery( false );
                    }
                } else if (HintTypes.ROUTE.equalsIgnoreCase( name )) {
                    hintRouteInfo = value;
                }
                //进入下一批次处理。
                pos = mark;
            }
        }
    }

    /**
     * 需要强行指定路由，否则仅在baseNode执行。
     *
     * @param lexer
     */
    private void parseTruncate(Lexer lexer) {
        lexer.check( Token.TRUNCATE );
        lexer.nextToken();
        lexer.check( Token.TABLE );
        lexer.nextToken();
        //解析表名
        parseTableName( lexer );
        if (!lexer.isEOF()) {
            lexer.skipToEOF();
        }
        splitSubSql( lexer );
    }

    /**
     * 需要强行指定路由，否则仅在baseNode执行。
     *
     * @param lexer
     */
    private void parseRename(Lexer lexer) {
        lexer.check( Token.RENAME );
        lexer.nextToken();
        lexer.check( Token.TABLE );
        lexer.nextToken();
        if (lexer.token() == Token.IDENTIFIER) {
            //表名
            splitSubSql( lexer );
            String tableName = lexer.stringVal();
            putTableRouteData( null, tableName, null );
        }
        if (!lexer.isEOF()) {
            lexer.skipToEOF();
        }
        splitSubSql( lexer );
    }

    /**
     * 需要强行指定路由，否则仅在baseNode执行。
     *
     * @param lexer
     */
    private void parseDrop(Lexer lexer) {
        lexer.check( Token.DROP );
        lexer.nextToken();
        //过滤掉TEMPORARY关键字
        if (lexer.token() == Token.TEMPORARY) {
            lexer.nextToken();
        }
        if (lexer.token() == Token.TABLE) {
            lexer.nextToken();
            lexer.skipTo( Token.IDENTIFIER );
            //解析表名
            parseTableName( lexer );
        } else if (lexer.token() == Token.INDEX) {
            lexer.skipTo( Token.ON );
            lexer.nextToken();
            //解析表名
            parseTableName( lexer );
        } else {
            //通过设置parseResult，不让cmdQuery再返回数据。
            this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORTED CMD: " + parseResult.getSourceSql() );
            return;
        }
        if (!lexer.isEOF()) {
            lexer.skipToEOF();
        }
        splitSubSql( lexer );
    }

    /**
     * 需要强行指定路由，否则仅在baseNode执行。
     *
     * @param lexer
     */
    private void parseAlter(Lexer lexer) {
        lexer.check( Token.ALTER );
        lexer.nextToken();
        if (lexer.token() == Token.TABLE) {
            lexer.nextToken();
            //解析表名
            parseTableName( lexer );
        } else {
            //通过设置parseResult，不让cmdQuery再返回数据。
            this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORTED CMD: " + parseResult.getSourceSql() );
            return;
        }
        if (!lexer.isEOF()) {
            lexer.skipToEOF();
        }
        splitSubSql( lexer );
    }

    /**
     * 需要强行指定路由，否则仅在baseNode执行。
     *
     * @param lexer
     */
    private void parseCreate(Lexer lexer) {
        lexer.check( Token.CREATE );
        lexer.nextToken();
        //过滤掉TEMPORARY关键字
        if (lexer.token() == Token.TEMPORARY) {
            lexer.nextToken();
        }
        if (lexer.token() == Token.TABLE) {
            lexer.nextToken();
            lexer.skipTo( Token.IDENTIFIER );
            //解析表名
            parseTableName( lexer );
        } else if (lexer.token() == Token.INDEX) {
            lexer.skipTo( Token.ON );
            lexer.nextToken();
            //解析表名
            parseTableName( lexer );
        } else {
            //剩下的类型都不支持，直接返回报错吧。
            //通过设置parseResult，不让cmdQuery再返回数据。
            this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORTED CMD: " + parseResult.getSourceSql() );
            return;
        }
        if (!lexer.isEOF()) {
            lexer.skipToEOF();
        }
        splitSubSql( lexer );
    }

    /**
     * 需要强行指定路由，否则仅在baseNode执行。
     *
     * @param lexer
     */
    private void parseExplain(Lexer lexer) {
        if (!lexer.isEOF()) {
            lexer.nextToken();
            if (lexer.token() == HINT) {
                parseHint( lexer );
                lexer.nextToken();
            }
            switch (lexer.token()) {
                case HINT:
                case COMMENT:
                case LINE_COMMENT:
                case MULTI_LINE_COMMENT:
                case SELECT:
                    this.parseResult.setMasterQuery( false );
                    parseSelect( lexer );
                    break;
                case INSERT:
                    parseInsert( lexer );
                    break;
                case UPDATE:
                    parseUpdate( lexer );
                    break;
                case DELETE:
                    parseDelete( lexer );
                    break;
                case REPLACE:
                    parseReplace( lexer );
                    break;
                default:
                    break;
            }
            //explain 给schema默认数据。
//            RouteAlgorithm.RouteInfoData routeInfoData = new RouteAlgorithm.RouteInfoData();
//            routeInfoData.setSingle(new RouteAlgorithm.RouteInfo(schema.getBaseNode(), schema.getName(), mainRouteData.tableConfig.getTableName()));
//            mainRouteData.routeInfoData = routeInfoData;
        }

    }

    /**
     * 解析select语句。
     *
     * @param lexer
     */
    private void parseSelect(Lexer lexer) {
        //直接找到From。
        lexer.skipTo( Token.FROM );
        //解析表内容
        parseTableInfo( lexer );
        //原计划在这做优化，结果子查询不能重写了
//        if (!checkRouteKeyExists()) {
//            lexer.skipToEOF();
//            splitSubSql(lexer);
//            return;
//        }
        //跳到where关键字,查找匹配。
        lexer.skipTo( Token.WHERE );
        parseWhereInfo( lexer );
        //直接走到eof
        if (!lexer.isEOF()) {
            lexer.skipToEOF();
        }
        //截断最后的sql。
        splitSubSql( lexer );
    }

    /**
     * 获得RouteData。
     *
     * @param table
     * @return
     */
    private TableRouteData getRouteData(String table) {
        if (mainTableRouteData != null) {
            if (mainTableRouteData.tableConfig.getTableName().equals( table ) || mainTableRouteData.tableConfig.getAliasName().equals( table )) {
                return mainTableRouteData;
            }
        }
        if (tableRouteDataList != null) {
            for (TableRouteData tableRouteData : tableRouteDataList) {
                if (tableRouteData.tableConfig.getTableName().equals( table ) || tableRouteData.tableConfig.getAliasName().equals( table )) {
                    return tableRouteData;
                }
            }
        }
        return null;
    }

    /**
     * 防止RouteData信息。
     */
    private void putTableRouteData(String database, String tableName, String aliasName) {
        TableRouteData tableRouteData = getRouteData( tableName );
        if (tableRouteData == null) {
            //构造新的
            tableRouteData = new TableRouteData();
            tableRouteData.tableConfig = MydbConfigService.getTableConfig( tableName );
            //如果不是配置项的，route=null，而且没有keyData
            if (tableRouteData.tableConfig == null) {
                tableRouteData.tableConfig = new TableConfig( tableName, 1, database );
            }
            //如果有route信息的，拉一下routeKeyData。
            if (tableRouteData.tableConfig.getRouteId() > 0) {
                RouteManager.initRouteKeyData( tableRouteData.tableConfig, routeKeyData );
            }
            if (aliasName != null) {
                tableRouteData.tableConfig.setAliasName( aliasName );
            }
        }
        //优先放mainRouteData
        if (mainTableRouteData == null) {
            mainTableRouteData = tableRouteData;
        } else {
            if (this.tableRouteDataList == null) {
                this.tableRouteDataList = new ArrayList<>();
            }
            tableRouteDataList.add( tableRouteData );
        }
    }


    /**
     * 解析出表名。
     *
     * @param lexer
     */
    private void parseTableName(Lexer lexer) {
        if (lexer.token() == Token.IDENTIFIER) {
            splitSubSql( lexer );
            String schemaName = null;
            String tableName = lexer.stringVal();
            lexer.nextToken();
            if (lexer.token() == Token.DOT) {
                lexer.nextToken();
                //刚刚是库名，现在是表名了
                schemaName = tableName;
                setLexerPos();
                tableName = lexer.stringVal();
                lexer.nextToken();
            }
            putTableRouteData( schemaName, tableName, null );
        }
    }

    /**
     * 解析insert语句。
     * 先匹配table，然后匹配字段位置。
     * 根据字段位置来取参。
     *
     * @param lexer
     */
    private void parseInsert(Lexer lexer) {
        lexer.check( Token.INSERT );
        lexer.nextToken();
        lexer.check( Token.INTO );
        lexer.nextToken();
        //解析表名
        parseTableName( lexer );
        //原计划在这做优化，可能导致子查询不能重写
        if (!routeKeyData.checkKeyExists()) {
            lexer.skipToEOF();
            splitSubSql( lexer );
            return;
        }
        //如果有routeData匹配，采取匹配routeKeyData
        //此时走values的路
        if (lexer.token() == Token.LPAREN) {
            int pos = 0;
            while (!lexer.isEOF()) {
                lexer.nextToken();
                if (lexer.token() == Token.IDENTIFIER) {
                    String colName = lexer.stringVal();
                    RouteAlgorithm.RouteKeyValue routeValue = routeKeyData.getValue( colName );
                    //设置pos位置
                    if (routeValue != null) {
                        routeValue.setType( pos + 100 );
                    }
                } else if (lexer.token() == Token.COMMA) {
                    pos++;
                } else if (lexer.token() == Token.RPAREN) {
                    //可以直接退了
                    break;
                }
                //如果匹配完了，则退出。
            }
            lexer.skipTo( Token.VALUES );
            lexer.nextToken();
            lexer.check( Token.LPAREN );
            pos = 0;
            while (!lexer.isEOF()) {
                lexer.nextToken();
                if (lexer.token() == Token.COMMA) {
                    pos++;
                } else if (lexer.token() == Token.RPAREN) {
                    //可以直接退了
                    break;
                } else {
                    if (routeKeyData.isSingle()) {
                        RouteAlgorithm.RouteKeyValue rkv = routeKeyData.getValue();
                        if (rkv.getType() == pos + 100) {
                            rkv.putValue( lexer.paramValueString() );
                            //匹配完了，直接退
                            break;
                        }
                    } else {
                        RouteAlgorithm.RouteKeyValue[] rkvs = routeKeyData.getValues();
                        for (RouteAlgorithm.RouteKeyValue rkv : rkvs) {
                            if (rkv.getType() == pos + 100) {
                                rkv.putValue( lexer.paramValueString() );
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (!lexer.isEOF()) {
            lexer.skipToEOF();
        }
        splitSubSql( lexer );
    }

    /**
     * 解析update语句。
     *
     * @param lexer
     */
    private void parseUpdate(Lexer lexer) {
        lexer.check( Token.UPDATE );
        //解析表内容
        parseTableInfo( lexer );
        //原计划在这做优化，结果子查询不能重写了

        //        if (!checkRouteKeyExists()) {
//            lexer.skipToEOF();
//            splitSubSql(lexer);
//            return;
//        }
        //跳到where关键字
        lexer.skipTo( Token.WHERE );
        parseWhereInfo( lexer );
        if (!lexer.isEOF()) {
            lexer.skipToEOF();
        }

        splitSubSql( lexer );
    }


    /**
     * 解析replace info语句
     *
     * @param lexer
     */
    private void parseReplace(Lexer lexer) {

    }

    /**
     * 解析delete语句。
     *
     * @param lexer
     */
    private void parseDelete(Lexer lexer) {
        lexer.check( Token.DELETE );
        lexer.nextToken();
        lexer.check( Token.FROM );
        //解析表内容
        parseTableInfo( lexer );
        //原计划在这做优化，结果子查询不能重写了
//        if (!checkRouteKeyExists()) {
//            lexer.skipToEOF();
//            splitSubSql(lexer);
//            return;
//        }
        //解析Where
        //跳到where关键字
        lexer.skipTo( Token.WHERE );
        parseWhereInfo( lexer );
        if (!lexer.isEOF()) {
            lexer.skipToEOF();
        }
        splitSubSql( lexer );
    }

    /**
     * 解析Where。
     *
     * @param lexer
     */
    private void parseWhereInfo(Lexer lexer) {
        //开始尝试匹配routeKey
        while (!lexer.isEOF()) {
            lexer.nextToken();
            switch (lexer.token()) {
                case IDENTIFIER:
                    //属性值的情况，检查匹配。
                    String colName = lexer.stringVal();
                    lexer.nextToken();
                    if (lexer.token() == Token.DOT) {
                        lexer.nextToken();
                    }
                    if (lexer.token() == Token.IDENTIFIER) {
                        colName = lexer.stringVal();
                        lexer.nextToken();
                    }
                    RouteAlgorithm.RouteKeyValue routeValue = routeKeyData.getValue( colName );
                    if (routeValue != null) {
                        //判断操作符，取参数。
                        switch (lexer.token()) {
                            case EQ:
                                lexer.nextToken();
                                if (lexer.token() == Token.IDENTIFIER) {
                                    break;
                                }
                                routeValue.putValue( lexer.paramValueString() );
                                break;
                            case GT:
                                lexer.nextToken();
                                if (lexer.token() == Token.IDENTIFIER) {
                                    break;
                                }
                                routeValue.putRangeStart( lexer.paramValueString() );
                                break;
                            case GTEQ:
                                lexer.nextToken();
                                if (lexer.token() == Token.IDENTIFIER) {
                                    break;
                                }
                                routeValue.putRangeStart( lexer.paramValueString() );
                                break;
                            case LT:
                                lexer.nextToken();
                                if (lexer.token() == Token.IDENTIFIER) {
                                    break;
                                }
                                routeValue.putRangeEnd( lexer.paramValueString() );
                                break;
                            case LTEQ:
                                lexer.nextToken();
                                if (lexer.token() == Token.IDENTIFIER) {
                                    break;
                                }
                                routeValue.putRangeEnd( lexer.paramValueString() );
                                break;
                            case BANGEQ:
                                lexer.nextToken();
                                if (lexer.token() == Token.IDENTIFIER) {
                                    break;
                                }
                                routeValue.putRangeEnd( lexer.paramValueString() );
                                break;
                            case IN:
                                lexer.nextToken();
                                lexer.check( Token.LPAREN );
                                lexer.nextToken();
                                //处理子查询的情况。
                                if (lexer.token() == Token.SELECT) {
                                    parseSelect( lexer );
                                    break;
                                }
                                ArrayList<String> vs = new ArrayList<>();
                                while (!lexer.isEOF()) {
                                    if (lexer.token() == Token.RPAREN) {
                                        break;
                                    } else if (lexer.token() == Token.COMMA) {
                                        break;
                                    } else {
                                        vs.add( lexer.paramValueString() );
                                    }
                                    lexer.nextToken();
                                }
                                routeValue.putValues( vs );
                                break;
                            default:
                                break;
                        }
                    }
                    break;
                case SELECT:
                    //里面有嵌套子查询！
                    parseSelect( lexer );
                    break;
                default:
                    //不管了，让他过
                    break;
            }
            //判断如果都满足了，直接结束。
//            if (!routeKeyData.isEmptyValue()) {
//                break;
//            }
        }

    }


    /**
     * 解析TableInfo。
     * 包含表名和别名。
     *
     * @param lexer
     */
    private void parseTableInfo(Lexer lexer) {
        while (!lexer.isEOF()) {
            lexer.nextToken();
            switch (lexer.token()) {
                case IDENTIFIER:
                    //此处截断sql
                    splitSubSql( lexer );
                    String database = null, tableName = null, aliasName = null;
                    //说明是表名，进入检查。
                    tableName = lexer.stringVal();
                    lexer.nextToken();
                    if (lexer.token() == Token.DOT) {
                        lexer.nextToken();
                        //刚刚是库名，现在是表名了
                        database = tableName;
                        setLexerPos();
                        tableName = lexer.stringVal();
                        lexer.nextToken();
                    }
                    if (lexer.token() == Token.AS) {
                        lexer.nextToken();
                    }
                    if (lexer.token() == Token.IDENTIFIER) {
                        aliasName = lexer.stringVal();
                    }
                    //注册表到routeData
                    putTableRouteData( database, tableName, aliasName );
                    lexer.skipTo( Token.SET, Token.WHERE, Token.JOIN, Token.COMMA );
                    break;
                case JOIN:
                    //此处截断sql
                    splitSubSql( lexer );
                    String databaseJoin = null, tableJoin = null, aliasJoin = null;
                    //说明是表名，进入检查。
                    tableJoin = lexer.stringVal();
                    lexer.nextToken();
                    if (lexer.token() == Token.DOT) {
                        lexer.nextToken();
                        databaseJoin = tableJoin;
                        setLexerPos();
                        //刚刚是库名，现在是表名了
                        tableJoin = lexer.stringVal();
                        lexer.nextToken();
                    }
                    //判断as
                    if (lexer.token() == Token.AS) {
                        lexer.nextToken();
                    }
                    //判断alias
                    if (lexer.token() == Token.IDENTIFIER) {
                        aliasJoin = lexer.stringVal();
                    }

                    putTableRouteData( databaseJoin, tableJoin, aliasJoin );
                    //其他的就跳走吧，不管了。
                    lexer.skipTo( Token.SET, Token.WHERE, Token.JOIN, Token.COMMA );
                    break;
                case SELECT:
                    parseSelect( lexer );
                    break;
                default:
                    break;
            }
            //如果发现时where，则跳出
            if (lexer.token() == Token.WHERE || lexer.token() == Token.SET) {
                break;
            }
        }
    }

    /**
     * 增加子sql
     */
    private void splitSubSql(Lexer lexer) {
        String sqlSource = parseResult.getSourceSql();
        if (lexerPos >= sqlSource.length() - 1) {
            return;
        }
        if (lexer.isEOF() && lexerPos > 0) {
            subSqlList.add( sqlSource.substring( lexerPos ) );
        } else {
            subSqlList.add( sqlSource.substring( lexerPos, lexer.currentMark() ) );
        }
        lexerPos = lexer.currentPos();
    }


    /**
     * 设置LexerPos
     */
    private void setLexerPos() {
        lexerPos = lexer.currentPos();
    }

    /**
     * 计算路由。
     * hint路由拥有最高优先级。
     * schema的默认路由为最后保障。
     */
    private void calculateAllRouteInfo() {
        //检查有没有hint，hint是强行匹配的，不再考虑其他条件。
        if (hintRouteInfo != null) {
            Set<DataTable> routeInfos = new HashSet<>();
            //此处强行指定路由。
            if ("*".equalsIgnoreCase( hintRouteInfo )) {
                // 匹配全部路由
                if (mainTableRouteData.tableConfig.getRouteId() > 0) {
                    try {
                        List<DataTable> list = RouteManager.getAllRouteList( mainTableRouteData.tableConfig );
                        routeInfos.addAll( list );
                    } catch (RouteAlgorithm.RouteException e) {
                        this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NO_ROUTE_INFO, "EXCEPTION:" + e.toString() + ", SQL: " + parseResult.getSourceSql() );
                        return;
                    }
                }

            } else {
                //指定路由列表
                String[] routes = hintRouteInfo.split( "," );
                for (String route : routes) {
                    String[] rs = route.split( "\\." );
                    if (rs.length == 2) {
                        routeInfos.add( new DataTable( new DataNode( Long.parseLong( rs[0] ), rs[1] ), mainTableRouteData.tableConfig.getTableName() ) );
                    } else if (rs.length == 3) {
                        routeInfos.add( new DataTable( new DataNode( Long.parseLong( rs[0] ), rs[1] ), rs[2] ) );
                    } else {
                        this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NO_ROUTE_INFO, "EXCEPTION: HINT INFO ERROR! SQL: " + parseResult.getSourceSql() );
                        return;
                    }
                }
            }
            //对于要写数据，可能会是ddl操作或者重要操作，考虑也更新默认schema.
            if (parseResult.isMasterQuery()) {
                routeInfos.add( new DataTable( mainTableRouteData.tableConfig.getBaseNode(), mainTableRouteData.tableConfig.getTableName() ) );
            }
            RouteAlgorithm.RouteResultData routeInfoData = new RouteAlgorithm.RouteResultData();
            routeInfoData.setAll( routeInfos );
            mainTableRouteData.routeInfoData = routeInfoData;
        } else {
            //计算主表路由数据
            calculateTableRouteInfo( mainTableRouteData );
            //匹配从表路由信息
            if (tableRouteDataList != null) {
                for (TableRouteData tableRouteData : tableRouteDataList) {
                    calculateTableRouteInfo( tableRouteData );
                }
            }
        }
    }

    /**
     * 计算表路由信息。
     *
     * @param tableRouteData
     */
    private void calculateTableRouteInfo(TableRouteData tableRouteData) {
        if (tableRouteData == null || tableRouteData.routeInfoData == null) {
            //这种情况一般是完全无法匹配的，生成sql的时候直接给默认schema。
            return;
        }
        //初始化默认路由
        DataTable defaultRoute = tableRouteData.tableConfig.genDataTable();
        //匹配主表路由数据，如果匹配到数据，则按照数据走，否则直接走默认表。
        if (tableRouteData != null && tableRouteData.tableConfig.getRouteId() > 0) {
            //如果Table是有Route数值的
            if (!routeKeyData.isEmptyValue()) {
                try {
                    tableRouteData.routeInfoData = RouteManager.calculate( tableRouteData.tableConfig, defaultRoute, routeKeyData );
                } catch (Throwable e) {
                    this.parseResult.setErrorInfo( MySqlErrorCode.ERR_ROUTE_CALC, "ROUTE CALC ERROR: " + e.getMessage() + ", SQL: " + parseResult.getSourceSql() );
                    log.warn( "ROUTE CALC ERROR: " + e.getMessage() + ", SQL: " + parseResult.getSourceSql() );
                    return;
                }
            }
        }
        if (routeKeyData.isEmptyValue()) {
            //在路由名单里的，不指定参数，根据匹配类型确定转发。
            switch (MydbRouteMatchMode.findByValue( tableRouteData.tableConfig.getMatchType() )) {
                case MATCH_DEFAULT:
                    //此时是非sharding配置表，给schema默认数据。
                    tableRouteData.routeInfoData = new RouteAlgorithm.RouteResultData();
                    tableRouteData.routeInfoData.setSingle( defaultRoute );
                    break;
                case MATCH_ALL:
                    //匹配全部路由
                    try {
                        tableRouteData.routeInfoData.setAll( new HashSet<>( RouteManager.getAllRouteList( tableRouteData.tableConfig ) ) );
                    } catch (Throwable e) {
                        this.parseResult.setErrorInfo( MySqlErrorCode.ERR_ROUTE_CALC, "ROUTE CALC ERROR: " + e.getMessage() + ", SQL: " + parseResult.getSourceSql() );
                        return;
                    }
                    break;
                default:
                    //直接报错吧。
                    this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NO_ROUTE_KEY, "NO ROUTE KEY[" + routeKeyData.keyString() + "]:" + parseResult.getSourceSql() );
            }
        }
    }

    /**
     * 根据路由情况，分批合并sql。
     */
    private void generateSqlInfo() {
        //没有匹配到表名，直接给默认schema了。
        if (subSqlList.size() <= 1 && mainTableRouteData == null) {
            return;
        }
        //每个mainRouteInfoData对应一个mysqlGroup
        if (checkSingleRoute()) {
            sqlInfo = new SqlParseResult.SqlInfo( parseResult.getSourceSql().length() + 64 );
            //开始循环加表名
            for (int i = 0; i < subSqlList.size(); i++) {
                sqlInfo.appendSql( subSqlList.get( i ) );
                if (i == 0) {
                    //把主表路由加上。
                    DataTable dataTable = mainTableRouteData.routeInfoData.getRouteResult();
                    if (dataTable != null) {
                        sqlInfo.appendSql( dataTable.getDatabase() ).appendSql( "." ).appendSql( dataTable.getTable() );
                        sqlInfo.setDataTable( dataTable );
                    }
                } else if (i < subSqlList.size() - 1) {
                    //开始处理从表路由。
                    if (tableRouteDataList != null) {
                        RouteAlgorithm.RouteResultData routeInfoData = tableRouteDataList.get( i - 1 ).routeInfoData;
                        if (routeInfoData != null) {
                            DataTable dataTable = routeInfoData.getRouteResult();
                            sqlInfo.appendSql( dataTable.checkValid() ? dataTable.getDatabase() : sqlInfo.getDatabase() ).appendSql( "." ).appendSql( dataTable.getTable() );
                        }
                    }
                }
            }
            this.parseResult.setSqlInfo( sqlInfo );
        } else {
            sqlInfoList = new ArrayList<>();
            SqlParseResult.SqlInfo sb = new SqlParseResult.SqlInfo( parseResult.getSourceSql().length() + 64 );
            sqlInfoList.add( sb );
            //开始循环加表名
            for (int i = 0; i < subSqlList.size(); i++) {
                for (SqlParseResult.SqlInfo sqlInfo : sqlInfoList) {
                    sqlInfo.appendSql( subSqlList.get( i ) );
                }
                if (i == 0) {
                    //把主表路由加上。
                    appendRouteInfoData( true, mainTableRouteData.routeInfoData );
                } else if (i < subSqlList.size() - 1) {
                    //开始处理从表路由。
                    if (tableRouteDataList != null) {
                        RouteAlgorithm.RouteResultData routeInfoData = tableRouteDataList.get( i - 1 ).routeInfoData;
                        if (routeInfoData != null) {
                            appendRouteInfoData( false, routeInfoData );
                        }
                    }
                }
            }
        }
        this.parseResult.setSqlInfoList( sqlInfoList );

    }

    /**
     * 是否为单一路由？
     *
     * @return
     */
    private boolean checkSingleRoute() {
        boolean isSingle = mainTableRouteData.isSingle();
        if (tableRouteDataList != null) {
            for (TableRouteData tableRouteData : tableRouteDataList) {
                isSingle = isSingle && tableRouteData.isSingle();
            }
        }
        return isSingle;
    }

    /**
     * 附加路由信息数据。
     *
     * @param routeResultData
     */
    private void appendRouteInfoData(boolean isMain, RouteAlgorithm.RouteResultData routeResultData) {
        if (routeResultData.isSingle()) {
            //匹配单个结果。
            DataTable dataTable = routeResultData.getRouteResult();
            for (SqlParseResult.SqlInfo sqlInfo : sqlInfoList) {
                sqlInfo.appendSql( dataTable.checkValid() ? dataTable.getDatabase() : sqlInfo.getDatabase() ).appendSql( "." ).appendSql( dataTable.getTable() );
                if (isMain) {
                    sqlInfo.setDataTable( dataTable );
                }
            }
        } else {
            ArrayList<SqlParseResult.SqlInfo> sqlInfoList = new ArrayList<>();
            for (DataTable dataTable : routeResultData.getRouteResults()) {
                //此处应该复制多个sql了。。。
                for (SqlParseResult.SqlInfo sqlInfo : this.sqlInfoList) {
                    SqlParseResult.SqlInfo sqlInfo1 = new SqlParseResult.SqlInfo( parseResult.getSourceSql().length() + 32 );
                    sqlInfo1.appendSql( sqlInfo.getNewSql() );
                    sqlInfo1.setDataTable( sqlInfo.getDataTable() );
                    sqlInfo1.appendSql( dataTable.checkValid() ? dataTable.getDatabase() : sqlInfo1.getDatabase() ).appendSql( "." ).appendSql( dataTable.getTable() );
                    if (isMain) {
                        sqlInfo1.setDataTable( dataTable );
                    }
                    sqlInfoList.add( sqlInfo1 );
                }
            }
            this.sqlInfoList = sqlInfoList;
        }
    }

    /**
     * 表路由信息。
     */
    private static class TableRouteData {

        /**
         * 表信息。
         */
        TableConfig tableConfig;

        /**
         * 绑定的路由结果数据。
         */
        RouteAlgorithm.RouteResultData routeInfoData;

        /**
         * 是否单一路由
         *
         * @return
         */
        public boolean isSingle() {
            if (routeInfoData != null) {
                return routeInfoData.isSingle();
            } else {
                return true;
            }
        }
    }

}
