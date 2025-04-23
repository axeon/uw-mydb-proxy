package uw.mydb.proxy.sqlparse;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;
import uw.mydb.proxy.conf.MydbProxyConfigService;
import uw.mydb.proxy.constant.SQLType;
import uw.mydb.proxy.protocol.constant.MySqlErrorCode;
import uw.mydb.proxy.route.RouteAlgorithm;
import uw.mydb.proxy.route.RouteManager;
import uw.mydb.proxy.server.ProxySession;
import uw.mydb.proxy.sqlparse.parser.HintTypes;
import uw.mydb.proxy.sqlparse.parser.Lexer;
import uw.mydb.proxy.sqlparse.parser.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static uw.mydb.proxy.sqlparse.parser.Token.*;

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
     * 主路由信息，存储表信息和路由结果。
     */
    private TableRouteData tableRouteDataMain;

    /**
     * 路由信息列表，除了主路由之外的子表路由。
     */
    private List<TableRouteData> tableRouteDataList;

    /**
     * sql解析结果。
     */
    private SqlParseResult parseResult;

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
                    this.parseResult.setMasterQuery( false );
                    this.parseResult.setSqlType( SQLType.SELECT.getValue() );
                    parseSelect( lexer );
                    break;
                case INSERT:
                    this.parseResult.setSqlType( SQLType.INSERT.getValue() );
                    parseInsert( lexer );
                    break;
                case UPDATE:
                    this.parseResult.setSqlType( SQLType.UPDATE.getValue() );
                    parseUpdate( lexer );
                    break;
                case DELETE:
                    this.parseResult.setSqlType( SQLType.DELETE.getValue() );
                    parseDelete( lexer );
                    break;
                case USE:
                    this.parseResult.setSqlType( SQLType.OTHER.getValue() );
                    parseUse( lexer );
                    break;
                default:
                    this.parseResult.setSqlType( SQLType.OTHER.getValue() );
                    if (lexer.token() == SET || lexer.token() == SHOW || lexer.token() == EXPLAIN || lexer.token() == DESCRIBE || lexer.token() == EOF) {
                        //有些类型需要通过虚拟schema上支持的,，这些类型必须可以过。
                    } else {
                        //剩下的类型都不支持，直接返回报错吧。
                        parseResult.setErrorInfo( MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORTED CMD: " + parseResult.getSourceSql() );
                    }
                    break;
            }
        }

        if (!parseResult.hasError()) {
            //计算路由信息。
            calculateAllRouteInfo();
        }
        if (!parseResult.hasError()) {
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
     * 解析select语句。
     *
     * @param lexer
     */
    private void parseSelect(Lexer lexer) {
        //直接找到From。
        lexer.skipTo( Token.FROM );
        //解析表内容
        parseTableInfo( lexer );
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
     * 解析出表名。
     *
     * @param lexer
     */
    private void parseTableName(Lexer lexer) {
        if (lexer.token() == Token.IDENTIFIER) {
            splitSubSql( lexer );
            String database = null;
            String tableName = lexer.stringVal();
            lexer.nextToken();
            if (lexer.token() == Token.DOT) {
                lexer.nextToken();
                //刚刚是库名，现在是表名了
                database = tableName;
                setLexerPos();
                tableName = lexer.stringVal();
                lexer.nextToken();
            }
            putTableRouteData( database, tableName, null );
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
        //此处做优化，如果没有匹配表，则直接返回。
        if (!checkExistsRouteData()) {
            lexer.skipToEOF();
            splitSubSql( lexer );
            return;
        }
        //此时走values的路
        if (lexer.token() == Token.LPAREN) {
            int pos = 0;
            while (!lexer.isEOF()) {
                lexer.nextToken();
                if (lexer.token() == Token.IDENTIFIER) {
                    String tableName = null;
                    String colName = lexer.stringVal();
                    lexer.nextToken();
                    if (lexer.token() == Token.DOT) {
                        lexer.nextToken();
                        tableName = colName;
                        colName = lexer.stringVal();
                    }
                    RouteAlgorithm.RouteValue routeValue = findRouteValue( tableName, colName );
                    //此处利用routeValueType字段设置pos位置，后期还要用guessType设置回去的。
                    if (routeValue != null) {
                        routeValue.setType( pos + 1000 );
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
                    if (tableRouteDataMain.routeData.isSingle()) {
                        RouteAlgorithm.RouteValue rkv = tableRouteDataMain.routeData.getValue();
                        if (rkv.getType() == pos + 1000) {
                            rkv.putValue( lexer.paramValueString() );
                            //匹配完了，直接退
                            break;
                        }
                    } else {
                        RouteAlgorithm.RouteValue[] rkvs = tableRouteDataMain.routeData.getValues();
                        for (RouteAlgorithm.RouteValue rkv : rkvs) {
                            if (rkv.getType() == pos + 1000) {
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
        //跳到where关键字
        lexer.skipTo( Token.WHERE );
        parseWhereInfo( lexer );
        if (!lexer.isEOF()) {
            lexer.skipToEOF();
        }
        splitSubSql( lexer );
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
                    String tableName = null;
                    String colName = lexer.stringVal();
                    lexer.nextToken();
                    if (lexer.token() == Token.DOT) {
                        lexer.nextToken();
                        tableName = colName;
                        colName = lexer.stringVal();
                        lexer.nextToken();
                    }
                    RouteAlgorithm.RouteValue routeValue = findRouteValue( tableName, colName );
                    if (routeValue != null && routeValue.isEmpty()) {
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
                            case GTEQ:
                                lexer.nextToken();
                                if (lexer.token() == Token.IDENTIFIER) {
                                    break;
                                }
                                routeValue.putRangeStart( lexer.paramValueString() );
                                break;
                            case LT:
                            case LTEQ:
                                lexer.nextToken();
                                if (lexer.token() == Token.IDENTIFIER) {
                                    break;
                                }
                                routeValue.putRangeEnd( lexer.paramValueString() );
                                break;
                            case BETWEEN:
                                lexer.nextToken();
                                routeValue.putRangeStart( lexer.paramValueString() );
                                lexer.nextToken();
                                if (lexer.token() == AND) {
                                    lexer.nextToken();
                                    routeValue.putRangeEnd( lexer.paramValueString() );
                                }
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
     * 获取RouteData。
     *
     * @param tableName
     * @return
     */
    private RouteAlgorithm.RouteData findRouteData(String tableName) {
        if (tableRouteDataMain != null) {
            if (tableName.equals( tableRouteDataMain.tableConfig.getTableName() ) || tableName.equals( tableRouteDataMain.getTableAliasName() )) {
                return tableRouteDataMain.routeData;
            }
        }
        if (tableRouteDataList != null) {
            for (TableRouteData tableRouteData : tableRouteDataList) {
                if (tableName.equals( tableRouteData.tableConfig.getTableName() ) || tableName.equals( tableRouteData.getTableAliasName() )) {
                    return tableRouteData.routeData;
                }
            }
        }
        return null;
    }

    /**
     * 检查是否匹配到路由数据。
     *
     * @return
     */
    private boolean checkExistsRouteData() {
        return tableRouteDataMain != null && tableRouteDataMain.routeData != null;
    }

    /**
     * 查找RouteValue。
     *
     * @param tableName
     * @param routeKey
     * @return
     */
    private RouteAlgorithm.RouteValue findRouteValue(String tableName, String routeKey) {
        RouteAlgorithm.RouteData routeData = tableRouteDataMain.routeData;
        if (tableName != null) {
            routeData = findRouteData( tableName );
        }
        if (routeData != null) {
            if (routeData.isSingle()) {
                return routeData.getValue();
            } else {
                return routeData.getValue( routeKey );
            }
        }
        return null;
    }

    /**
     * 放置RouteData信息。
     */
    private void putTableRouteData(String database, String tableName, String aliasName) {
        //从配置中拉取table配置
        TableConfig tableConfig = MydbProxyConfigService.getTableConfig( tableName );
        //如果没有配置的，直接返回吧。
        if (tableConfig == null) {
            tableConfig = new TableConfig( tableName, MydbProxyConfigService.getBaseClusterId(), database );
            //加入本地缓存，减少不必要的落地查询。
            MydbProxyConfigService.putTableConfigToLocalCache( tableName, tableConfig );
        }
        TableRouteData tableRouteData = new TableRouteData( tableConfig, aliasName );
        //如果有route信息的，拉一下routeKeyData。
        if (tableConfig.getRouteId() > 0) {
            tableRouteData.setRouteData( RouteManager.initRouteData( tableConfig ) );
        }
        //优先放mainRouteData
        if (this.tableRouteDataMain == null) {
            this.tableRouteDataMain = tableRouteData;
            this.parseResult.setSourceTable( tableName );
        } else {
            if (this.tableRouteDataList == null) {
                this.tableRouteDataList = new ArrayList<>();
            }
            tableRouteDataList.add( tableRouteData );
        }
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
                if (tableRouteDataMain.tableConfig.getRouteId() > 0) {
                    try {
                        List<DataTable> list = RouteManager.getAllRouteList( tableRouteDataMain.tableConfig );
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
                        routeInfos.add( new DataTable( new DataNode( Long.parseLong( rs[0] ), rs[1] ), tableRouteDataMain.tableConfig.getTableName() ) );
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
                routeInfos.add( new DataTable( tableRouteDataMain.tableConfig.getBaseNode(), tableRouteDataMain.tableConfig.getTableName() ) );
            }
            RouteAlgorithm.RouteResult routeInfoData = new RouteAlgorithm.RouteResult();
            routeInfoData.setAll( routeInfos );
            tableRouteDataMain.routeResult = routeInfoData;
        } else {
            if (tableRouteDataMain != null) {
                //计算主表路由数据
                calculateTableRouteInfo( tableRouteDataMain );
                //匹配从表路由信息
                if (tableRouteDataList != null) {
                    for (TableRouteData tableRouteData : tableRouteDataList) {
                        calculateTableRouteInfo( tableRouteData );
                    }
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
        if (tableRouteData.routeData == null) {
            //这种情况一般是完全无法匹配的，生成sql的时候直接给默认schema。
            return;
        }
        //匹配主表路由数据，如果匹配到数据，则按照数据走，否则直接走默认表。
        if (tableRouteData.tableConfig.getRouteId() > 0) {
            //如果Table是有Route数值的
            try {
                tableRouteData.routeResult = RouteManager.calculate( tableRouteData.tableConfig, tableRouteDataMain.routeData );
            } catch (Throwable e) {
                this.parseResult.setErrorInfo( MySqlErrorCode.ERR_ROUTE_CALC, "ROUTE CALC ERROR: " + e.getMessage() + ", SQL: " + parseResult.getSourceSql() );
//                log.warn( "ROUTE CALC ERROR: {}, SQL: {}", e.getMessage(), parseResult.getSourceSql() );
            }
        }
    }

    /**
     * 整体结果是否为单一路由？
     *
     * @return
     */
    private boolean checkSingleRoute() {
        boolean isSingle = tableRouteDataMain.isSingleRoute();
        if (tableRouteDataList != null) {
            for (TableRouteData tableRouteData : tableRouteDataList) {
                isSingle = isSingle && tableRouteData.isSingleRoute();
            }
        }
        return isSingle;
    }

    /**
     * 根据路由情况，分批合并sql。
     */
    private void generateSqlInfo() {
        //没有匹配到表名，直接给默认schema了。
        if (subSqlList.size() <= 1 || tableRouteDataMain == null) {
            this.parseResult.setSqlInfo( new SqlParseResult.SqlInfo( DataTable.newDataWithClusterId( MydbProxyConfigService.getBaseClusterId() ), parseResult.sourceSql ) );
            return;
        }
        //每个mainRouteInfoData对应一个mysqlGroup
        if (checkSingleRoute()) {
            SqlParseResult.SqlInfo sqlInfo = new SqlParseResult.SqlInfo( parseResult.getSourceSql().length() + 64 );
            //开始循环加表名
            for (int i = 0; i < subSqlList.size(); i++) {
                sqlInfo.appendSql( subSqlList.get( i ) );
                if (i == 0) {
                    //把主表路由加上。
                    DataTable dataTable = tableRouteDataMain.getSingleRouteResult();
                    if (dataTable != null) {
                        sqlInfo.appendSql( dataTable.genSqlIdentity() );
                        sqlInfo.setDataTable( dataTable );
                    }
                } else if (i < subSqlList.size() - 1) {
                    //开始处理从表路由。
                    if (tableRouteDataList != null) {
                        DataTable dataTable = tableRouteDataList.get( i - 1 ).getSingleRouteResult();
                        if (dataTable != null) {
                            sqlInfo.appendSql( dataTable.genSqlIdentity() );
                        }
                    }
                }
            }
            this.parseResult.setSqlInfo( sqlInfo );
        } else {
            List<SqlParseResult.SqlInfo> sqlInfoList = new ArrayList<>();
            SqlParseResult.SqlInfo sb = new SqlParseResult.SqlInfo( parseResult.getSourceSql().length() + 64 );
            sqlInfoList.add( sb );
            //开始循环加表名
            for (int i = 0; i < subSqlList.size(); i++) {
                for (SqlParseResult.SqlInfo sqlInfo : sqlInfoList) {
                    sqlInfo.appendSql( subSqlList.get( i ) );
                }
                if (i == 0) {
                    //把主表路由加上。
                    appendRouteInfoData( true, tableRouteDataMain );
                } else if (i < subSqlList.size() - 1) {
                    //开始处理从表路由。
                    if (tableRouteDataList != null) {
                        appendRouteInfoData( false, tableRouteDataList.get( i - 1 ) );
                    }
                }
            }
            this.parseResult.sqlInfoList = sqlInfoList;
        }

    }

    /**
     * 附加路由信息数据。
     *
     * @param tableRouteData
     */
    private void appendRouteInfoData(boolean isMain, TableRouteData tableRouteData) {
        if (tableRouteData.isSingleRoute()) {
            //匹配单个结果。
            DataTable dataTable = tableRouteData.getSingleRouteResult();
            for (SqlParseResult.SqlInfo sqlInfo : this.parseResult.sqlInfoList) {
                sqlInfo.appendSql( dataTable.genSqlIdentity() );
                if (isMain) {
                    sqlInfo.setDataTable( dataTable );
                }
            }
        } else {
            ArrayList<SqlParseResult.SqlInfo> sqlInfoList = new ArrayList<>();
            for (DataTable dataTable : tableRouteData.routeResult.getDataTables()) {
                //此处应该复制多个sql了。。。
                for (SqlParseResult.SqlInfo sqlInfo : this.parseResult.sqlInfoList) {
                    SqlParseResult.SqlInfo sqlInfo1 = new SqlParseResult.SqlInfo( parseResult.getSourceSql().length() + 32 );
                    sqlInfo1.appendSql( sqlInfo.getNewSql() );
                    sqlInfo1.setDataTable( sqlInfo.getDataTable() );
                    sqlInfo1.appendSql( dataTable.genSqlIdentity() );
                    if (isMain) {
                        sqlInfo1.setDataTable( dataTable );
                    }
                    sqlInfoList.add( sqlInfo1 );
                }
            }
            this.parseResult.sqlInfoList = sqlInfoList;
        }
    }

    /**
     * 表路由信息。
     */
    public static class TableRouteData {

        /**
         * 表信息。
         */
        protected TableConfig tableConfig;

        /**
         * 表别名。
         */
        protected String tableAliasName;

        /**
         * 路由数据。
         */
        protected RouteAlgorithm.RouteData routeData;

        /**
         * 绑定的路由结果数据。
         */
        protected RouteAlgorithm.RouteResult routeResult;

        public TableRouteData(TableConfig tableConfig) {
            this.tableConfig = tableConfig;
        }

        public TableRouteData(TableConfig tableConfig, String tableAliasName) {
            this.tableConfig = tableConfig;
            this.tableAliasName = tableAliasName;
        }

        public TableRouteData() {
        }

        /**
         * 是否单一路由。
         *
         * @return
         */
        public boolean isSingleRoute() {
            return routeResult == null || routeResult.isSingle();
        }

        /**
         * 获取单表路由结果。
         * 单表路由结果很有可能没有经过计算的。
         */
        public DataTable getSingleRouteResult() {
            if (routeResult == null) {
                return tableConfig.genDataTable();
            } else {
                return routeResult.getDataTable();
            }
        }

        public TableConfig getTableConfig() {
            return tableConfig;
        }

        public void setTableConfig(TableConfig tableConfig) {
            this.tableConfig = tableConfig;
        }

        public String getTableAliasName() {
            return tableAliasName;
        }

        public void setTableAliasName(String tableAliasName) {
            this.tableAliasName = tableAliasName;
        }

        public RouteAlgorithm.RouteData getRouteData() {
            return routeData;
        }

        public void setRouteData(RouteAlgorithm.RouteData routeData) {
            this.routeData = routeData;
        }

        public RouteAlgorithm.RouteResult getRouteResult() {
            return routeResult;
        }

        public void setRouteResult(RouteAlgorithm.RouteResult routeResult) {
            this.routeResult = routeResult;
        }
    }
}
