package com.mobilesw.homey;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

public class IntroduceTeam extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_introduce_team);

        getSupportActionBar().setTitle("About us");
    }
}
