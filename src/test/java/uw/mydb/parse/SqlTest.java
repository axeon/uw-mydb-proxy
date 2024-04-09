package uw.mydb.parse;

import uw.cache.FusionCache;
import uw.mydb.vo.TableConfig;

/**
 * 测试sql。
 */
public class SqlTest {

    static void init(){
        FusionCache.config( new FusionCache.Config( TableConfig.class, 100, -1 ) );
        FusionCache.put( TableConfig.class,"test_table",new TableConfig("test_table",1,"test"),true );
    }

    static String database = null;

    /**
     * 普通插入。
     */
     static String insert = "insert into test_table\n" +
            "(saas_id,distributor_mch_id,channel_room_id,channel_hotel_id,channel_room_name,sys_hotel_id,sys_roomtype_id,\n" +
            "channel_room_state,channel_room_match_state,channel_bed_type,create_date,modify_date)\n" +
            "values (10002,10009,40965089034,24733008034,'杨过4房','10001_587517','10001_587517_764478',0,0,'大床','2018-07-03 14:56:29.2')";
    /**
     * 普通插入。
     */
     static String insertNoCols = "insert into alitrip_hotel_room \n" +
            "values (10002,10009,40965089034,24733008034,'杨过4房','10001_587517','10001_587517_764478',0,0,'大床','2018-07-03 14:56:29.2')";
    /**
     * 批量插入
     */
     static String insertBatch = "insert into alitrip_hotel_room\n" +
            "(saas_id,distributor_mch_id,channel_room_id,channel_hotel_id,channel_room_name,sys_hotel_id,sys_roomtype_id,\n" +
            "channel_room_state,channel_room_match_state,channel_bed_type,create_date,modify_date)\n" +
            "values (10002,10009,40965089034,24733008034,'杨过4房','10001_587517','10001_587517_764478',0,0,'大床','2018-07-03 14:56:29.2'),"+
            " (10002,10009,40965089034,24733008034,'杨过4房','10001_587517','10001_587517_764478',0,0,'大床','2018-07-03 14:56:29.2'),"+
            " (10002,10009,40965089034,24733008034,'杨过4房','10001_587517','10001_587517_764478',0,0,'大床','2018-07-03 14:56:29.2'),"+
            " (10002,10009,40965089034,24733008034,'杨过4房','10001_587517','10001_587517_764478',0,0,'大床','2018-07-03 14:56:29.2'),"+
            " (10002,10009,40965089034,24733008034,'杨过4房','10001_587517','10001_587517_764478',0,0,'大床','2018-07-03 14:56:29.2')";

    /**
     * 普通更新。
     */
     static String update = "update test_table set create_date=now() where saas_id=1000 ";

    /**
     * 带子查询更新。
     */
     static String updateWithSubSelect = "update user_info set create_date=now() where saas_id=1000 and id in (select id from user_info where state>0)";

    /**
     * 删除。
     */
     static String delete = "update test_table set create_date=now() where saas_id=1000 ";

    /**
     * 带子查询删除。
     */
     static String deleteWithSubSelect = "update user_info set create_date=now() where saas_id=1000 and id in (select id from user_info where state>0)";

    /**
     * 普通查询。
     */
     static String select ="select * from test_table where id=1000 ";

    /**
     * 带子查询查询。
     */
     static String selectWithSubSelect = "select * from sbtest1 WHERE id in (select id from sbtest1)";

    /**
     * 普通多表查询。
     */
     static String selectMultiTable ="select * from user_info a,user_detail b where a.id=b.id and a.id>0 and a.saas_id=10000";

    /**
     * 多表join查询。
     */
     static String selectJoinTable ="select * from user_info a left join user_detail b on a.id=b.id where a.id>0 and a.saas_id=10000";

}
