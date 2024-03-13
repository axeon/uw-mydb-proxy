package uw.mydb.sqlparser;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.conf.MydbConfigService;
import uw.mydb.constant.MydbRouteMatchMode;
import uw.mydb.protocol.util.MySqlErrorCode;
import uw.mydb.proxy.ProxyMysqlSession;
import uw.mydb.route.RouteAlgorithm;
import uw.mydb.route.RouteManager;
import uw.mydb.sqlparser.parser.HintTypes;
import uw.mydb.sqlparser.parser.Lexer;
import uw.mydb.sqlparser.parser.Token;
import uw.mydb.vo.DataNode;
import uw.mydb.vo.DataTable;
import uw.mydb.vo.TableConfig;

import java.util.*;

import static uw.mydb.constant.MydbRouteMatchMode.MATCH_DEFAULT;
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
    private ProxyMysqlSession proxySession;

    /**
     * sql所在的schema
     */
    private String database;

    /**
     * 原始sql语句。
     */
    private String srcSql;

    /**
     * lexer解析器。
     */
    private Lexer lexer;

    /**
     * 被分割的子sql。
     */
    private List<String> subSqls = new ArrayList<>();

    /**
     * 整个SQL关联的routeKeyData
     */
    private RouteAlgorithm.RouteKeyData routeKeyData = new RouteAlgorithm.RouteKeyData();

    /**
     * 主路由信息。
     */
    private TableRouteData mainTableRouteData;

    /**
     * 路由信息Map，除了主路由之外的子表路由。
     */
    private Map<String, TableRouteData> routeDataMap;

    /**
     * table列表。
     */
    private List<String> tableList;

    /**
     * sql解析结果。
     */
    private SqlParseResult parseResult;

    /**
     * lexer解析位置，用于sql分割。
     */
    private int lexerPos = 0;

    /**
     * 是否是DML.
     */
    private boolean isDML;

    /**
     * 单sql结果。
     */
    private SqlParseResult.SqlInfo sqlInfo;

    /**
     * 多sql结果。
     */
    private ArrayList<SqlParseResult.SqlInfo> sqlInfos = null;

    /**
     * hint的Route信息。
     */
    private String hintRouteInfo = null;

    /**
     * 默认构造器。
     *
     * @param proxySession
     * @param srcSql
     */
    public SqlParser(ProxyMysqlSession proxySession, String srcSql) {
        this.proxySession = proxySession;
        this.database = proxySession.getDatabase();
        this.srcSql = srcSql;
        this.lexer = new Lexer( srcSql, false, true );
        this.parseResult = new SqlParseResult( database, srcSql );
    }

    /**
     * 默认构造器。
     *
     * @param database
     * @param srcSql
     */
    public SqlParser(String database, String srcSql) {
        this.database = database;
        this.srcSql = srcSql;
        this.lexer = new Lexer( srcSql, false, true );
        this.parseResult = new SqlParseResult( database, srcSql );
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
                    this.isDML = true;
                    this.parseResult.setMasterIfNull( false );
                    parseSelect( lexer );
                    break;
                case INSERT:
                    this.isDML = true;
                    parseInsert( lexer );
                    break;
                case UPDATE:
                    this.isDML = true;
                    parseUpdate( lexer );
                    break;
                case DELETE:
                    this.isDML = true;
                    parseDelete( lexer );
                    break;
                case REPLACE:
                    this.isDML = true;
                    parseReplace( lexer );
                    break;
                case CREATE:
                    parseCreate( lexer );
                    break;
                case ALTER:
                    parseAlter( lexer );
                    break;
                case DROP:
                    parseDrop( lexer );
                    break;
                case TRUNCATE:
                    parseTruncate( lexer );
                    break;
                case EXPLAIN:
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
                        parseResult.setErrorInfo( MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORTED CMD: " + srcSql );
                    }
                    break;
            }
        }
        //设置table
        if (this.mainTableRouteData != null && this.mainTableRouteData.tableConfig != null) {
            this.parseResult.setTable( this.mainTableRouteData.tableConfig.getTableName() );
        }

        if (!parseResult.hasError()) {
            //如果master未设置，处于保险，设置为true
            this.parseResult.setMasterIfNull( true );
            //计算路由信息。
            calculateRouteInfo();
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
                        parseResult.setMaster( true );
                    } else {
                        parseResult.setMaster( false );
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
            putRouteData( null, tableName, null );
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
            this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORTED CMD: " + srcSql );
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
            this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORTED CMD: " + srcSql );
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
            this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORTED CMD: " + srcSql );
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
                    this.parseResult.setMaster( false );
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
     * 构造RouteData。
     *
     * @return RouteData
     */
    private TableRouteData buildRouteData(String table) {
        TableRouteData tableRouteData = new TableRouteData();
        tableRouteData.tableConfig = MydbConfigService.getTableConfig( table );
        //如果不是配置项的，route=null，而且没有keyData
        if (tableRouteData.tableConfig == null) {
            tableRouteData.tableConfig = new TableConfig();
            tableRouteData.tableConfig.setTableName( table );
        }
        //如果有route信息的，拉一下routeKeyData。
        if (tableRouteData.tableConfig.getRouteId() > 0) {
            RouteManager.getParamMap( routeKeyData, tableRouteData.tableConfig );
        }
        return tableRouteData;
    }

    /**
     * 检查是否有数据库表配置匹配
     * 一些情况下，是匹配不到任何table的，这时候就不用匹配keyData了。
     *
     * @return
     */
    private boolean checkTableRouteExists() {
        if (mainTableRouteData != null && mainTableRouteData.tableConfig.getRouteId() > 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 检查是否有路由Key匹配。
     * 一些情况下，是匹配不到任何table的，这时候就不用匹配keyData了。
     *
     * @return
     */
    private boolean checkRouteKeyExists() {
        return routeKeyData.checkKeyExists();
    }

    /**
     * 获得RouteData。
     *
     * @param table
     * @return
     */
    private TableRouteData getRouteData(String table) {
        TableRouteData tableRouteData = null;
        if (mainTableRouteData != null) {
            if (mainTableRouteData.tableConfig.getTableName().equals( table )) {
                return mainTableRouteData;
            }
        }
        if (routeDataMap != null) {
            tableRouteData = routeDataMap.get( table );
            if (tableRouteData != null) {
                return tableRouteData;
            }
        }
        return tableRouteData;
    }

    /**
     * 放置tableConfig。
     */
    private void putRouteData(String schemaName, String tableName, String aliasName) {
        TableRouteData tableRouteData = getRouteData( tableName );
        //已经有了，直接返回。
        if (tableRouteData == null) {
            //构造新的
            tableRouteData = buildRouteData( tableName );
            tableRouteData.database = schemaName;
        }
        //优先放mainRouteData
        if (mainTableRouteData == null) {
            mainTableRouteData = tableRouteData;
        } else {
            //多表的，放集合内
            if (tableList == null) {
                tableList = new ArrayList<>();
            }
            tableList.add( tableName );
            if (routeDataMap == null) {
                routeDataMap = new HashMap<>();
            }
            routeDataMap.put( tableName, tableRouteData );
        }
        //添加别名
        if (aliasName != null) {
            if (routeDataMap == null) {
                routeDataMap = new HashMap<>();
            }
            routeDataMap.put( aliasName, tableRouteData );
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
            putRouteData( schemaName, tableName, null );
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
        if (!checkRouteKeyExists()) {
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
                        Collection<RouteAlgorithm.RouteKeyValue> rkvs = routeKeyData.getValues();
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
     * 解析TableInfo
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
                    String schemaName = null, tableName = null, aliasName = null;
                    //说明是表名，进入检查。
                    tableName = lexer.stringVal();
                    lexer.nextToken();
                    if (lexer.token() == Token.DOT) {
                        lexer.nextToken();
                        //刚刚是库名，现在是表名了
                        schemaName = tableName;
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
                    putRouteData( schemaName, tableName, aliasName );
                    lexer.skipTo( Token.SET, Token.WHERE, Token.JOIN, Token.COMMA );
                    break;
                case JOIN:
                    //此处截断sql
                    splitSubSql( lexer );
                    String schemaJoin = null, tableJoin = null, aliasJoin = null;
                    //说明是表名，进入检查。
                    tableJoin = lexer.stringVal();
                    lexer.nextToken();
                    if (lexer.token() == Token.DOT) {
                        lexer.nextToken();
                        //刚刚是库名，现在是表名了
                        schemaJoin = tableJoin;
                        setLexerPos();
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

                    putRouteData( schemaJoin, tableJoin, aliasJoin );
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
        if (lexerPos >= srcSql.length() - 1) {
            return;
        }
        if (lexer.isEOF() && lexerPos > 0) {
            subSqls.add( srcSql.substring( lexerPos ) );
        } else {
            subSqls.add( srcSql.substring( lexerPos, lexer.currentMark() ) );
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
    private void calculateRouteInfo() {

        //检查有没有hint，hint是强行匹配的
        if (hintRouteInfo != null) {
            //此处强行指定路由。
            if ("*".equalsIgnoreCase( hintRouteInfo )) {
                // 匹配全部路由
                if (mainTableRouteData.tableConfig.getRouteId() > 0) {
                    this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NO_ROUTE_INFO, "NO TABLE ROUTE INFO: " + srcSql );
                    return;
                }
                List<DataTable> list = null;
                try {
                    list = RouteManager.getAllRouteList( mainTableRouteData.tableConfig );
                } catch (RouteAlgorithm.RouteException e) {
                    this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NO_ROUTE_INFO, "NO TABLE ROUTE INFO: " + srcSql );
                    return;
                }
                //对于要写数据，可能会是ddl操作或者重要操作，考虑也更新默认schema.
                if (parseResult.isMaster()) {
                    list.add( new DataTable( mainTableRouteData.tableConfig.getBaseNode(), mainTableRouteData.tableConfig.getTableName() ) );
                }
                RouteAlgorithm.RouteResultData routeInfoData = new RouteAlgorithm.RouteResultData();
                routeInfoData.setAll( new HashSet<>( list ) );
                mainTableRouteData.routeInfoData = routeInfoData;
            } else {
                //指定路由列表
                HashSet<DataTable> list = new HashSet<>();
                String[] routes = hintRouteInfo.split( "," );
                for (String route : routes) {
                    String[] rs = route.split( "\\." );
                    if (rs.length == 2) {
                        list.add( new DataTable( new DataNode( Long.parseLong( rs[0] ), rs[1] ), null ) );
                    }
                }
                RouteAlgorithm.RouteResultData routeInfoData = new RouteAlgorithm.RouteResultData();
                routeInfoData.setAll( list );
                mainTableRouteData.routeInfoData = routeInfoData;
            }
        } else {

            if (mainTableRouteData == null) {
                //这种情况一般是完全无法匹配的，生成sql的时候直接给默认schema。
                return;
            }
            //外部強行賦值的，直接返回。
            if (mainTableRouteData.routeInfoData != null) {
                return;
            }

            //检查是否有表匹配。
            if (checkTableRouteExists()) {
                //此时Table是有Route的
                if (!routeKeyData.isEmptyValue()) {
                    //此时说明是sharding配置表。
                    DataTable defaultRoute =  mainTableRouteData.tableConfig.genDataTable();
                    try {
                        mainTableRouteData.routeInfoData = RouteManager.calculate( mainTableRouteData.tableConfig, defaultRoute, routeKeyData );
                    } catch (Exception e) {
                        if (mainTableRouteData.tableConfig.getMatchType() == MATCH_DEFAULT.getValue()) {
                            //此时匹配错，给schema默认数据，导向默认节点。
                            RouteAlgorithm.RouteResultData routeInfoData = new RouteAlgorithm.RouteResultData();
                            routeInfoData.setSingle( defaultRoute );
                            mainTableRouteData.routeInfoData = routeInfoData;
                        } else {
                            this.parseResult.setErrorInfo( MySqlErrorCode.ERR_ROUTE_CALC, "ROUTE CALC ERROR: " + e.getMessage() + ", SQL: " + srcSql );
                            log.warn( "ROUTE CALC ERROR: " + e.getMessage() + ", SQL: " + srcSql );
                            return;
                        }
                    }
                } else {
                    //在路由名单里的，不指定参数，根据匹配类型确定转发。
                    switch (MydbRouteMatchMode.findByValue( mainTableRouteData.tableConfig.getMatchType() )) {
                        case MATCH_DEFAULT:
                            //此时是非sharding配置表，给schema默认数据。
                            RouteAlgorithm.RouteResultData routeInfoData = new RouteAlgorithm.RouteResultData();
                            routeInfoData.setSingle( mainTableRouteData.tableConfig.genDataTable() );
                            mainTableRouteData.routeInfoData = routeInfoData;
                            break;
                        case MATCH_ALL:
                            //匹配全部路由
                            RouteAlgorithm.RouteResultData routeInfoData2 = new RouteAlgorithm.RouteResultData();
                            try {
                                routeInfoData2.setAll( new HashSet<>( RouteManager.getAllRouteList( mainTableRouteData.tableConfig ) ) );
                            } catch (RouteAlgorithm.RouteException e) {
                                this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NO_ROUTE_INFO, "NO TABLE ROUTE INFO: " + srcSql );
                                return;
                            }
                            mainTableRouteData.routeInfoData = routeInfoData2;
                            break;
                        default:
                            //直接报错吧。
                            this.parseResult.setErrorInfo( MySqlErrorCode.ERR_NO_ROUTE_KEY, "NO ROUTE KEY[" + routeKeyData.keyString() + "]:" + srcSql );
                            return;
                    }
                }
            } else {
                //不在路由名单里的，匹配默认schema。
                RouteAlgorithm.RouteResultData routeInfoData = new RouteAlgorithm.RouteResultData();
                String schemaName = mainTableRouteData.database;
                if (schemaName == null) {
                    schemaName = database;
                } else {
                    //排除系统表。
                    if (!schemaName.equals( "information_schema" ) && !schemaName.equals( "performance_schema" ) && !schemaName.equals( "mysql" ) && !schemaName.equals( "sys" )) {
                        schemaName = database;
                    }
                }
                routeInfoData.setSingle( mainTableRouteData.tableConfig.genDataTable() );

                mainTableRouteData.routeInfoData = routeInfoData;
            }

            //匹配从表数据
            if (routeDataMap != null) {
                for (TableRouteData tableRouteData : routeDataMap.values()) {
                    if (tableRouteData.routeInfoData == null) {
                        try {
                            DataTable defaultRoute = mainTableRouteData.tableConfig.genDataTable();
                            tableRouteData.routeInfoData = RouteManager.calculate( tableRouteData.tableConfig, defaultRoute, routeKeyData );
                        } catch (Exception e) {
                            this.parseResult.setErrorInfo( MySqlErrorCode.ERR_ROUTE_CALC, "ROUTE CALC ERROR:  " + e.getMessage() + ", SQL: " + srcSql );
                            log.warn( "ROUTE CALC ERROR: " + e.getMessage() + ", SQL: " + srcSql );
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * 根据路由情况，分批合并sql。
     */
    private void generateSqlInfo() {
        //没有匹配到表名，直接给默认schema了。
        if (subSqls.size() <= 1 && mainTableRouteData == null) {
            sqlInfo = new SqlParseResult.SqlInfo( srcSql );
            // TODO 此处写死了有问题
            sqlInfo.setDataTable( new DataTable( new DataNode(1,database),null ) );
            this.parseResult.setSqlInfo( sqlInfo );
            return;
        }
        //每个mainRouteInfoData对应一个mysqlGroup
        if (checkSingleRoute()) {
            sqlInfo = new SqlParseResult.SqlInfo( srcSql.length() + 64 );
            //开始循环加表名
            for (int i = 0; i < subSqls.size(); i++) {
                sqlInfo.appendSql( subSqls.get( i ) );
                if (i == 0) {
                    //把主表路由加上。
                    DataTable dataTable = mainTableRouteData.routeInfoData.getRouteResult();
                    if (dataTable != null) {
                        sqlInfo.appendSql( dataTable.getDatabase() ).appendSql( "." ).appendSql( dataTable.getTable() );
                        sqlInfo.setDataTable( dataTable );
                    }
                } else if (i < subSqls.size() - 1) {
                    //开始处理从表路由。
                    if (tableList != null) {
                        RouteAlgorithm.RouteResultData routeInfoData = routeDataMap.get( tableList.get( i - 1 ) ).routeInfoData;
                        if (routeInfoData != null) {
                            DataTable routeInfo = routeInfoData.getRouteResult();
                            sqlInfo.appendSql( routeInfo.checkValid() ? routeInfo.getDatabase() : sqlInfo.getDatabase() ).appendSql( "." ).appendSql( routeInfo.getTable() );
                        }
                    }
                }
            }
            this.parseResult.setSqlInfo( sqlInfo );
        } else {
            sqlInfos = new ArrayList<>();
            SqlParseResult.SqlInfo sb = new SqlParseResult.SqlInfo( srcSql.length() + 32 );
            sqlInfos.add( sb );
            //开始循环加表名
            for (int i = 0; i < subSqls.size(); i++) {
                for (SqlParseResult.SqlInfo sqlInfo : sqlInfos) {
                    sqlInfo.appendSql( subSqls.get( i ) );
                }
                if (i == 0) {
                    //把主表路由加上。
                    appendRouteInfoData( true, mainTableRouteData.routeInfoData );
                } else if (i < subSqls.size() - 1) {
                    //开始处理从表路由。
                    if (tableList != null) {
                        RouteAlgorithm.RouteResultData routeInfoData = routeDataMap.get( tableList.get( i - 1 ) ).routeInfoData;
                        if (routeInfoData != null) {
                            appendRouteInfoData( false, routeInfoData );
                        }
                    }
                }
            }
        }
        this.parseResult.setSqlInfos( sqlInfos );

    }

    /**
     * 是否为单一路由？
     *
     * @return
     */
    private boolean checkSingleRoute() {
        boolean isSingle = mainTableRouteData == null || mainTableRouteData.isSingle();
        if (routeDataMap != null) {
            for (TableRouteData tableRouteData : routeDataMap.values()) {
                isSingle = isSingle && tableRouteData.isSingle();
            }
        }
        this.parseResult.setSingle( isSingle );
        return isSingle;
    }

    /**
     * 附加路由信息数据。
     *
     * @param rid
     */
    private void appendRouteInfoData(boolean isMain, RouteAlgorithm.RouteResultData rid) {
        if (rid.isSingle()) {
            //匹配单个结果。
            DataTable dataTable = rid.getRouteResult();
            for (SqlParseResult.SqlInfo si : sqlInfos) {
                si.appendSql( dataTable.checkValid() ? dataTable.getDatabase() : si.getDatabase() ).appendSql( "." ).appendSql( dataTable.getTable() );
                if (isMain) {
                    si.setDataTable( dataTable );
                }
            }
        } else {
            ArrayList<SqlParseResult.SqlInfo> sbxs = new ArrayList<>();
            for (DataTable dataTable : rid.getRouteResults()) {
                //此处应该复制多个sql了。。。
                for (SqlParseResult.SqlInfo sqlInfo : sqlInfos) {
                    SqlParseResult.SqlInfo sqlInfo1 = new SqlParseResult.SqlInfo( srcSql.length() + 32 );
                    sqlInfo1.appendSql( sqlInfo.getNewSql() );
                    sqlInfo1.setDataTable( sqlInfo.getDataTable() );
                    sqlInfo1.appendSql( dataTable.checkValid() ? dataTable.getDatabase() : sqlInfo1.getDatabase() ).appendSql( "." ).appendSql( dataTable.getTable() );
                    if (isMain) {
                        sqlInfo1.setDataTable( dataTable);
                    }
                    sbxs.add( sqlInfo1 );
                }
            }
            sqlInfos = sbxs;
        }
    }

    /**
     * 表路由信息。
     */
    private static class TableRouteData {

        /**
         * 原始的database信息。
         */
        String database;

        /**
         * 表信息。
         */
        TableConfig tableConfig;

        /**
         * 绑定的routeInfoData
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
