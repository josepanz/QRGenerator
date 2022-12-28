package com.bepsa.qrgenerator;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.qr.emvco.EMVCoParser;
import com.qr.emvco.EmvcoTlvBean;

import org.jpos.iso.ISOUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ReadQRActivity extends AppCompatActivity {

    final String TAG = "dev";
    ImageView imagen;
    TextView labelResultado;
    private List<String> tagWithSubTags = new ArrayList<>();
    JSONObject jsonObject;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read_qr);
        imagen = findViewById(R.id.imagen);
        labelResultado = findViewById(R.id.txtResultado);
        tagWithSubTags.add("02");
        tagWithSubTags.add("62");
        new IntentIntegrator(this).initiateScan(); // abre el escaner
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setContentView(R.layout.activity_main);
    }

    // se ejecuta al leer el qr o cerrar el scaner
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        String datos = result.getContents();
        String dataDefault = "00020101021202270012py.com.bepsa01070100001520470115303600541300000000015005802PY5921COMERCIO PRUEBA BEPSA6008ASUNCI�N61162022314500000031624503020105060001040725                         63102074716646";
        if (!Objects.isNull(datos) && !datos.trim().isEmpty()) {
            try {
                JSONObject request = translateToSsRestRequest(decodeQRData(datos));
                postTypeAsyncApi(request);
                ISOUtil.sleep(10000);
                if ("00".equals(jsonObject.getString("response_code")) && "APPROVED".equalsIgnoreCase(jsonObject.getString("transaction_status"))) {
                    imagen.setVisibility(View.VISIBLE);
                    labelResultado.setVisibility(View.VISIBLE);
                    jsonObject = null;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
//        try {
//            JSONObject request = translateToSsRestRequest(decodeQRData(dataDefault));
//            postTypeAsyncApi(request);
//            ISOUtil.sleep(10000);
//            jsonObject = new JSONObject("{\"status\": \"success\",\"response_status_code\": 200,\"response_code\": \"00\",\"transaction_status\": \"APPROVED\",\"ticket_number\": \"0000000001\",\"retrieval_reference_number\": \"031100075402\",\"transaction_token\" : \"2022312300000009\",\"amount\": 10000,\"installment_number\": 1,\"currency\": 600,\"payer_data\": {\"account_number\": \"0123465789\",\"account_type\" : \"D\",\"document_number\" : \"0123456\",\"payer_cellphone\" : \"0971234567\",\"payer_email\": \"juanperez@entidad.com.py\",\"payer_name\" : \"Juan\",\"payer_lastname\": \"Perez\"},\"entity_data\" : {\"entity_transaction_id\": \"1020\",\"entity_description\" : \"Banco Continental\"},\"card_data\": {\"card_number\" : \"542434******1020\"} }");
//            if ("00".equals(jsonObject.getString("response_code")) && "APPROVED".equalsIgnoreCase(jsonObject.getString("transaction_status"))) {
//                imagen.setVisibility(View.VISIBLE);
//                labelResultado.setVisibility(View.VISIBLE);
//                jsonObject = null;
//            }
//        } catch (JSONException je) {
//            je.printStackTrace();
//        }
    }


    public JSONObject translateToSsRestRequest(JSONObject req) throws JSONException {
        String baseData = "{\r\n    \"installment_number\": 1,\r\n    \"payer_data\": {\r\n        \"account_number\": \"1408487066\",\r\n        \"account_type\": \"D\",\r\n        \"payer_cellphone\": \"0971234567\",\r\n        \"payer_email\": \"juanperez@entidad.com.py\",\r\n        \"payer_name\": \"Juan\",\r\n        \"payer_lastname\": \"Perez\"\r\n    },\r\n    \"entity_data\": {\r\n        \"entity_transaction_id\": \"1020\",\r\n        \"entity_description\": \"Banco Continental\"\r\n    },\r\n    \"card_data\": {\r\n        \"card_number\": \"542434******1020\"\r\n    }\r\n}";
        JSONObject ret = new JSONObject(baseData);
        ret.put("transaction_token", req.get("transactionToken"));
        ret.put("amount", new BigDecimal(req.getString("amount")));
        ret.put("installment_number", 1);
        ret.put("currency", Integer.parseInt(req.getString("currencyCode")));
        System.out.println("SS_REST_REQUEST:");
        System.out.println(ret);
        return ret;
    }

    public JSONObject decodeQRData(String qrData) throws JSONException {
        Map<String, EmvcoTlvBean> emvcoTlvBeanMap = EMVCoParser.parse(qrData);
        System.out.println("\nPrint\tTag");
        EMVCoParser.printTag(emvcoTlvBeanMap);
        System.out.println("decode data: " + emvcoTlvBeanMap.toString());
        //SubTag
        final Map<String, Map<String, EmvcoTlvBean>> parseSubTlv = EMVCoParser.parseSubTlv(emvcoTlvBeanMap);
        for (Map.Entry<String, Map<String, EmvcoTlvBean>> subs : parseSubTlv.entrySet()) {
            System.out.println();
            EMVCoParser.printOneTlv(emvcoTlvBeanMap.get(subs.getKey()), subs.getKey());
            System.out.println("Print\tSubTag");
            EMVCoParser.printTag(subs.getValue());
        }

        return convertEmvCoTlvToJson(emvcoTlvBeanMap);
    }

    public JSONObject convertEmvCoTlvToJson(Map<String, EmvcoTlvBean> qrMap) throws JSONException {
        JSONObject response = new JSONObject();
        HashMap<String, String> map = getTlvMapToJson();
        HashMap<String, String> subMap = getSubTlvMapToJson();
        final Map<String, Map<String, EmvcoTlvBean>> parseSubTlv = EMVCoParser.parseSubTlv(qrMap);
        for (Map.Entry<String, EmvcoTlvBean> tlv : qrMap.entrySet()) {
            if (tagWithSubTags.contains(tlv.getKey())) {
                JSONObject subTags = new JSONObject();
                for (Map.Entry<String, EmvcoTlvBean> subTlv : parseSubTlv.get(tlv.getKey()).entrySet()) {
                    subTags.put(subMap.get(subTlv.getKey()), subTlv.getValue().getValue());
                }
                response.put(map.get(tlv.getKey()), subTags);
            } else {
                response.put(map.get(tlv.getKey()), tlv.getValue().getValue());
            }
        }
        System.out.println("JSONResponse: " + response.toString());
        return response;
    }

    public void postTypeAsyncApi(JSONObject jsonRequest) {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, jsonRequest.toString());
        Request request = new Request.Builder()
//                .url("http://192.170.195.51:11025/QRPayments/pay")
                .url("http://10.220.3.115:11025/QRPayments/pay")
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
                        jsonObject = new JSONObject(strResponse);
                        System.out.println("SS_REST_RESPONSE:");
                        System.out.println(jsonObject.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "onResponse " + strResponse);
                    Log.d(TAG, "onResponseJson " + jsonObject.toString());
                } else {
                    if (Objects.nonNull(response)) {
                        ResponseBody responseBody = response.body();
                        String strResponse = responseBody.string();
                        try {
                            jsonObject = new JSONObject(strResponse);
                            System.out.println("SS_REST_RESPONSE:");
                            System.out.println(jsonObject.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        Log.d(TAG, "onResponse " + strResponse);
                        Log.d(TAG, "onResponseJson " + jsonObject.toString());
                    }
                }
            }
        });
    }

    public static HashMap<String, String> getTlvMapToJson() {
        HashMap<String, String> map = new HashMap<>();
        map.put("00", "formatContentIndicator");
        map.put("01", "poi");
        map.put("02", "merchantData");
        map.put("52", "merchantCategoryCode");
        map.put("53", "currencyCode");
        map.put("54", "amount");
        map.put("58", "countryCode");
        map.put("59", "merchantName");
        map.put("60", "merchantCity");
        map.put("61", "transactionToken");
        map.put("62", "merchantAdditionalData");
        map.put("63", "crc");
        return map;
    }

    public static HashMap<String, String> getSubTlvMapToJson() {
        HashMap<String, String> map = new HashMap<>();
        map.put("00", "globalUniqueIdentifier");
        map.put("01", "merchantCode");
        map.put("03", "branchId");
        map.put("05", "stan");
        map.put("07", "terminalData");
        return map;
    }

    String jsonStringDefault = "{\n" +
            "    \"transaction_token\": 2022312300000009,\n" +
            "    \"amount\": 10000,\n" +
            "    \"installment_number\": 1,\n" +
            "    \"currency\": 600,\n" +
            "    \"payer_data\": {\n" +
            "        \"account_number\": \"0123465789\",\n" +
            "        \"account_type\": \"D\",\n" +
            "        \"payer_cellphone\": \"0971234567\",\n" +
            "        \"payer_email\": \"juanperez@entidad.com.py\",\n" +
            "        \"payer_name\": \"Juan\",\n" +
            "        \"payer_lastname\": \"Perez\"\n" +
            "    },\n" +
            "    \"entity_data\": {\n" +
            "        \"entity_transaction_id\": \"1020\",\n" +
            "        \"entity_description\": \"Banco Continental\"\n" +
            "    },\n" +
            "    \"card_data\": {\n" +
            "        \"card_number\": \"542434******1020\"\n" +
            "    }\n" +
            "}";
}