package com.bepsa.qrgenerator;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOUtil;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Generacion de un QR mediante la llamada a una api
 * que retorna datos para generar un QR EMVCo
 */
public class GenerateQRApiActivity extends AppCompatActivity {
    final String TAG = "dev";
    JSONObject jsonResponse ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_qr_api);

        EditText txtDatos = findViewById(R.id.txtDatos);
        Button btnGenerarQR = findViewById(R.id.btnGenerarQR);
        ImageView imgQR = findViewById(R.id.qrCode);

        btnGenerarQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String strAmount = txtDatos.getText().toString();
                if (!strAmount.trim().isEmpty()) {
                    if (strAmount.length() < 13) {
                        try {
                            BigDecimal inputAmount = new BigDecimal(strAmount);
                            postTypeAsyncApi(ISOUtil.zeropad(inputAmount.toString(), 12));

                            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                            Bitmap bitmap;
                            if (Objects.nonNull(jsonResponse)) {
                                System.out.println("JSONResponse:");
                                System.out.println(jsonResponse.toString());
                                System.out.println("QrData:<" + jsonResponse.get("qrData") + ">");
                                System.out.println("Token:<" + jsonResponse.get("qrToken") + ">");
                                bitmap = barcodeEncoder.encodeBitmap(jsonResponse.getString("qrData"), BarcodeFormat.QR_CODE, 750, 750);
                            } else
                                bitmap = barcodeEncoder.encodeBitmap(txtDatos.getText().toString(), BarcodeFormat.QR_CODE, 750, 750);

                            imgQR.setImageBitmap(bitmap);
                        } catch (WriterException | JSONException | ISOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Toast.makeText(GenerateQRApiActivity.this, "El monto debe ser menor a 13 Digitos", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(GenerateQRApiActivity.this, "Debe setear un monto", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void postTypeAsyncApi(String amount) throws JSONException {
        JSONObject jsonRequest = new JSONObject();
        jsonRequest.put("poi", "12");
        jsonRequest.put("merchantData", "py.com.bepsa11223344");
        jsonRequest.put("mcc", "5411");
        jsonRequest.put("amount", amount);
        jsonRequest.put("countryCode", "PY");
        jsonRequest.put("merchantName", "SUPERMAS");
        jsonRequest.put("merchantCity", "ASUNCION");
        jsonRequest.put("merchantAdditionalData", "SUCURSAL12345            000008                   TERMINAL12345            ");
        jsonRequest.put("qrTraceID", "1665782454180-020788-526766-ss-pos-55db8fbf74-7hfpp");
        jsonRequest.put("originalPcode", "540061");
        jsonRequest.put("convertedPcode", "000000");
        jsonRequest.put("ss", "SS-POS");
        jsonRequest.put("currencyCode", "600");
        jsonRequest.put("expirationTime", "60");

        System.out.println("JSONRequest: " + jsonRequest.toString());

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, jsonRequest.toString());
        Request request = new Request.Builder()
                .url("http://10.220.3.115:31050/api/cnp/qr/generate")
                .post(body)
                .addHeader("api-key", "123456")
                .addHeader("Content-Type", "application/json")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResponseBody responseBody = response.body();
                    String strResponse = responseBody.string();
                    try {
                        jsonResponse = new JSONObject(strResponse);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "onResponse " + strResponse);
                    Log.d(TAG, "onResponseJson " + jsonResponse.toString());
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setContentView(R.layout.activity_main);
    }


}