package com.bepsa.qrgenerator;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.jpos.core.Configuration;
import org.jpos.core.SimpleConfiguration;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.iso.packager.XMLPackager;
import org.jpos.q2.QBean;
import org.jpos.util.NameRegistrar;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.jpos.simulator.JPOSManager;


public class GenerateQRActivity extends AppCompatActivity {
    private String qrToken = "";
    private long start;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_qr);

        EditText txtDatos = findViewById(R.id.txtDatos);
        Button btnGenerarQR = findViewById(R.id.btnGenerarQR);
        ImageView imgQR = findViewById(R.id.qrCode);
        Button btnConsultarQR = findViewById(R.id.btnConsultarQR);

        /**
         * Metodo del boton btnGenerarQR que realiza el envio de un ISOMsg
         * empaquetado en formato XMLPackager para transacciones financieras
         * y por medio de la respuesta genera una imagen QR
         */
        btnGenerarQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String strAmount = txtDatos.getText().toString();
                if (!strAmount.trim().isEmpty()) {
                    if (strAmount.length() < 13) {
                        try {
                            ISOMsg request = new ISOMsg();
                            ISOMsg response = new ISOMsg();
                            // se prepara el mensaje para enviar
                            try {
                                request = getMessageMock("qr_generate_iso_message_request.xml");
                                BigDecimal inputAmount = new BigDecimal(strAmount);
                                request.set(4, ISOUtil.zeropad(inputAmount.toString(), 12));
                                System.out.println("REQUEST:");
                                System.out.println(toXML(request));
                                response = sendMessage(toXML(request));
                                closeKeyboard();
                            } catch (IOException | ISOException e) {
                                e.printStackTrace();
                            }

                            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                            Bitmap bitmap;
                            ISOUtil.sleep(50000);
                            if (response != null) {
                                System.out.println("RESPONSE:");
                                System.out.println(toXML(response));
                                if (response.hasField(106)) {
                                    System.out.println("QrData:<" + response.getString(106) + ">");
                                    System.out.println("Token:<" + response.getString(105) + ">");
                                    qrToken = response.getString(105);
                                    bitmap = barcodeEncoder.encodeBitmap(response.getString(106), BarcodeFormat.QR_CODE, 750, 750);
                                    imgQR.setImageBitmap(bitmap);
                                    ISOMsg getQRStatusRequest = getMessageMock("qr_get_qr_status_iso_message_request.xml");
                                    getQRStatusRequest.set(105, qrToken);
                                    start = System.currentTimeMillis();
                                    ISOMsg getQRStatusResponse = sendMessage(toXML(getQRStatusRequest));
                                    ISOUtil.sleep(25000);
                                    if (getQRStatusResponse != null) {
                                        if ("00".equals(getQRStatusResponse.getString(39))) {
                                            Toast.makeText(GenerateQRActivity.this, "Transaccion Aprobada", Toast.LENGTH_LONG).show();
                                            ISOUtil.sleep(3000);
                                            onBackPressed();
                                        } else if ("P5".equals(getQRStatusResponse.getString(39))) {
                                            btnConsultarQR.setEnabled(true);
                                        } else {
                                            ISOUtil.sleep(3000);
                                            onBackPressed();
                                        }
                                    }
                                } else if (response.hasField(56)) {
                                    String [] qrDataValues = response.getString(56).split("\\|");
                                    qrToken = qrDataValues[1];
                                    System.out.println("QrData:<" + qrDataValues[2] + ">");
                                    System.out.println("Token:<" + qrToken + ">");
                                    bitmap = barcodeEncoder.encodeBitmap(qrDataValues[2], BarcodeFormat.QR_CODE, 750, 750);
                                    imgQR.setImageBitmap(bitmap);
                                    ISOMsg getQRStatusRequest = getMessageMock("qr_get_qr_status_iso_message_request.xml");
                                    getQRStatusRequest.set(56, qrToken);
                                    start = System.currentTimeMillis();
                                    ISOMsg getQRStatusResponse = sendMessage(toXML(getQRStatusRequest));
                                    ISOUtil.sleep(25000);
                                    if (getQRStatusResponse != null) {
                                        if ("00".equals(getQRStatusResponse.getString(39))) {
                                            Toast.makeText(GenerateQRActivity.this, "Transaccion Aprobada", Toast.LENGTH_LONG).show();
                                            ISOUtil.sleep(3000);
                                            onBackPressed();
                                        } else if ("P5".equals(getQRStatusResponse.getString(39))) {
                                            btnConsultarQR.setEnabled(true);
                                        } else {
                                            ISOUtil.sleep(3000);
                                            onBackPressed();
                                        }
                                    }
                                }
                                else {
                                    Toast.makeText(GenerateQRActivity.this, "No fue posible generar el QR.", Toast.LENGTH_LONG).show();
                                }
                            }
                        } catch (WriterException e) {
                            e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        Toast.makeText(GenerateQRActivity.this, "El monto debe ser menor a 13 Digitos", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(GenerateQRActivity.this, "Debe setear un monto", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /** Metodo que crear el mensaje pero en formato ISOMsg segun un packager definido
     * No es utilizado porque genera un error al formatear el mensaje debido al formato ISO establecido
     */
    private ISOMsg getMessage(String filename) throws IOException, ISOException {
        ISOMsg m = null;
        try {
            InputStream is = getAssets().open(filename);
            int length = is.available();
            byte[] data = new byte[length];
            is.read(data);
            m = new ISOMsg();
            try {
                GenericPackager p = new GenericPackager("jar:assets/verifone.xml");
                p.setHeaderLength(10);
                Configuration sc = new SimpleConfiguration();
                sc.put("packager-logger", "Q2");
                sc.put("packager-realm", "verifone-plain-channel");
                sc.put("packager-config", "jar:assets/verifone.xml");
                p.setConfiguration(sc);
                ISOPackager ip = (ISOPackager) p;
                m.setPackager(ip);
                m.unpack(data);
            } catch (ISOException e) {
                throw new ISOException("Error parsing '" + filename + "'", e);
            }
            m.recalcBitMap();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return m;
    }

    public void sendQuery(View view) throws Exception {
        ISOMsg getQRStatusRequest = getMessageMock("qr_get_qr_status_iso_message_request.xml");
        getQRStatusRequest.set(105, qrToken);
        ISOMsg getQRStatusResponse = sendMessage(toXML(getQRStatusRequest));
        ISOUtil.sleep(20000);
        if (getQRStatusResponse != null) {
            if ("00".equals(getQRStatusResponse.getString(39))) {
                Toast.makeText(GenerateQRActivity.this, "Transaccion Aprobada", Toast.LENGTH_LONG).show();
                ISOUtil.sleep(3000);
                onBackPressed();
            } else if ("P5".equals(getQRStatusResponse.getString(39))) {
                Toast.makeText(GenerateQRActivity.this, "Transaccion Pendiente. Vuelva a Consultar", Toast.LENGTH_LONG).show();
                if ((start - System.currentTimeMillis()) > 80000L){
                    Toast.makeText(GenerateQRActivity.this, "Tiempo para Consulta Excedido.", Toast.LENGTH_LONG).show();
                    ISOUtil.sleep(3000);
                    onBackPressed();
                }
            } else {
                ISOUtil.sleep(3000);
                onBackPressed();
            }
        }
    }

    private ISOMsg getMessageMock(String filename) throws ISOException {
        ISOMsg m = null;
        try {
            InputStream is = getAssets().open(filename);
            int length = is.available();
            byte[] data = new byte[length];
            is.read(data);
            m = new ISOMsg();
            try {
                m.setPackager(getDefaultPackager());
                m.unpack(data);
            } catch (ISOException e) {
                throw new ISOException("Error parsing '" + filename + "'", e);
            } catch (NoSuchFieldException e) {
                System.out.println("Error NoSuchFieldException " + e);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            m.recalcBitMap();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return m;
    }

    private XMLPackager getDefaultPackager() throws ISOException, NoSuchFieldException, IllegalAccessException {
        XMLPackager p = new XMLPackager();
        try {
            p.setXMLParserFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            p.setXMLParserFeature("http://xml.org/sax/features/external-general-entities", true);
            p.setXMLParserFeature("http://xml.org/sax/features/external-parameter-entities", true);
            return p;
        } catch (SAXException e) {
            throw new ISOException("Error creating XMLPackager", e);
        }
    }

    private String toXML(ISOMsg msg) throws Exception {
        final ISOMsg toDump = (ISOMsg) msg.clone();
        toDump.setPackager(null);
        toDump.setHeader((byte[]) null);
        toDump.setDirection(0);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final String utf8 = StandardCharsets.UTF_8.name();
        try (PrintStream ps = new PrintStream(baos, true, utf8)) {
            toDump.dump(ps, "");
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setContentView(R.layout.activity_main);
    }

    // Envia transacciones por socket
    private ISOMsg sendMessage(String xml) {
        ISOMsg isoResponse = new ISOMsg();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket s = new Socket("10.220.3.115", 11041);
                    OutputStream out = s.getOutputStream();
                    PrintWriter output = new PrintWriter(out);
                    output.println(xml);
                    Log.v("test", "========================= envia =========================");
                    output.flush();

                    BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    sb.append(input.readLine());
                    while (input.ready()) {
                        sb.append("\n");
                        sb.append(input.readLine());
                    }
                    String st = sb.toString();
                    Log.v("test", "========================= recibe =========================");
                    Log.v("test", "data: ");
                    Log.v("test", st);
                    InputStream is = new ByteArrayInputStream(st.getBytes());
                    isoResponse.setPackager(getDefaultPackager());
                    isoResponse.unpack(is);

                    output.close();
                    out.close();
                    s.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ISOException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
        return isoResponse;
    }

    /** Envia transacciones por socket usando metodo de lectura por jPOSManager
    * Clase que envia un mensaje en byte [] por medio de un socket, que podria tener
    * conexion segura SSL por certificado
    */
    private ISOMsg sendMessageSim(ISOMsg isoRequest, String xmlRequest) throws ISOException {
        Configuration sc = new SimpleConfiguration();
        sc.put("host:port", "10.220.3.115:11031");
        sc.put("trace", "true");

        JPOSManager jm = new JPOSManager();
        jm.setName("jpos");
        jm.setConfiguration(sc);
        jm.setState(QBean.STARTED);
        NameRegistrar.register(jm.getName(), jm);
        jm.startService();

        ISOMsg isoResponse = jm.sendISOMsg(isoRequest, xmlRequest);

        jm.stopService();
        return isoResponse;
    }

    /**
     * Cierra el teclado al seleccionar el boton generar
     */
    private void closeKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager im = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

    }
}