package com.humanix.tsw.bluenix;

import java.util.HashMap;

/**
 * Created by Administrator on 2015-10-06.
 */
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static final String BLUEINNO_PROFILE_SERVICE = "BLUEINNO_PROFILE_SERVICE";
    public static final String UUID_BLUEINNO_PROFILE_SERVICE_UUID = "00002220-0000-1000-8000-00805f9b34fb";
    public static final String BLUEINNO_PROFILE_SEND = "BLUEINNO_PROFILE_SEND";
    public static final String UUID_BLUEINNO_PROFILE_SEND_UUID = "00002222-0000-1000-8000-00805f9b34fb";
    public static final String BLUEINNO_PROFILE_RECEIVE = "BLUEINNO_PROFILE_RECEIVE";
    public static final String UUID_BLUEINNO_PROFILE_RECEIVE_UUID = "00002221-0000-1000-8000-00805f9b34fb";
    //public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        attributes.put(UUID_BLUEINNO_PROFILE_SERVICE_UUID, BLUEINNO_PROFILE_SERVICE);
        attributes.put(UUID_BLUEINNO_PROFILE_SEND_UUID, BLUEINNO_PROFILE_SEND);
        attributes.put(UUID_BLUEINNO_PROFILE_RECEIVE_UUID, BLUEINNO_PROFILE_RECEIVE);
    }

    public SampleGattAttributes() {}

    /*
    public static String lookup(String uuid, String defaultName) {
        String name = (String)attributes.get(uuid);
        return name == null?defaultName:name;
    }
    */
}
