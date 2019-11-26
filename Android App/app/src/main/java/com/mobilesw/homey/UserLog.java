package com.mobilesw.homey;

public class UserLog {

    private String userID;
    private String logDesc;
    private String date;

    public UserLog() {

    }

    public UserLog(String userID, String logDesc, String date) {
        this.userID = userID;
        this.logDesc = logDesc;
        this.date = date;
    }

}
