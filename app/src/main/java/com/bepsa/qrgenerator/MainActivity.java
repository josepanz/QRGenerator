package com.bepsa.qrgenerator;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void goToGenerateQR(View view) {
        Intent intent = new Intent(this, GenerateQRActivity.class);
        startActivity(intent);
    }

    public void goToReadQR(View view) {
        Intent intent = new Intent(this, ReadQRActivity.class);
        startActivity(intent);
    }

    public void goToGenerateQRForApi(View view) {
        Intent intent = new Intent(this, GenerateQRApiActivity.class);
        startActivity(intent);
    }

    public void goToGetQRStatus(View view) {
        Intent intent = new Intent(this, GetQRStatusActivity.class);
        startActivity(intent);
    }
}