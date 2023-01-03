package com.bepsa.qrgenerator;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.XMLPackager;
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
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Consulta de estado de la transaccion QR
 */
public class GetQRStatusActivity extends AppCompatActivity {
    EditText token;
    Button btnConsultarQR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_qr_status);
        initializeObjects();

        /**
         * Metodo del boton btnConsultarQR que realiza el envio de un ISOMsg
         * empaquetado en formato XMLPackager para transacciones financieras
         * y por medio de la respuesta te avisa del estado de la transaccion
         */
        btnConsultarQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String strAmount = token.getText().toString();
                processSelectedMessage(strAmount, "pos");
            }
        });
    }

    /**
     * Inicializar objetos del activity
     */

    private void initializeObjects() {
        token = findViewById(R.id.token);
        btnConsultarQR = findViewById(R.id.btnConsultarQR);
    }

    /**
     * Metodo que prepara el mensaje, toma el monto y envia al switch para consulta de datos de QR
     * Recibe como parametro el codigo de operacion a enviar para obtener el esquema del mensaje.
     * Informa del estado de la transaccion
     */
    private void processSelectedMessage(String str, String terminal) {
        if (!str.trim().isEmpty()) {
            if (str.length() == 16) {
                try {
                    ISOMsg request = new ISOMsg();
                    ISOMsg response = new ISOMsg();
                    // se prepara el mensaje para enviar
                    try {
                        request = getMessageMock(terminal + "/000062_qr_get_qr_status_iso_message_request.xml");
                        request.set(56, str);
                        System.out.println("REQUEST:");
                        System.out.println(toXML(request));
                        response = sendMessage(toXML(request));
                        closeKeyboard();
                    } catch (IOException | ISOException e) {
                        e.printStackTrace();
                    }

                    ISOUtil.sleep(5000);
                    if (response != null) {
                        System.out.println("RESPONSE:");
                        System.out.println(toXML(response));
                        if (terminal.equalsIgnoreCase("ATM")) {
                            String rc = response.getString(39);
                            System.out.println("Token:<" + response.getString(105) + ">");
                            if ("00".equals(rc)) {
                                Toast.makeText(GetQRStatusActivity.this, "Transaccion Aprobada " + rc, Toast.LENGTH_LONG).show();
                            } else if ("P5".equals(rc)) {
                                Toast.makeText(GetQRStatusActivity.this, "Transaccion Pendiente de Aprobacion " + rc, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(GetQRStatusActivity.this, "Transaccion Rechazada " + rc, Toast.LENGTH_LONG).show();
                            }
                        } else if (terminal.equalsIgnoreCase("POS")) {
                            String qrToken = response.getString(56);
                            String rc = response.getString(39);
                            System.out.println("Token:<" + qrToken + ">");
                            if ("00".equals(rc)) {
                                Toast.makeText(GetQRStatusActivity.this, "Transaccion Aprobada " + rc, Toast.LENGTH_LONG).show();
                            } else if ("P5".equals(rc)) {
                                Toast.makeText(GetQRStatusActivity.this, "Transaccion Pendiente de Aprobacion " + rc, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(GetQRStatusActivity.this, "Transaccion Rechazada " + rc, Toast.LENGTH_LONG).show();
                            }
                        }
                    } else {
                        Toast.makeText(GetQRStatusActivity.this, "No fue posible consultar el estado del QR.", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(GetQRStatusActivity.this, "El monto debe ser menor a 13 Digitos", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(GetQRStatusActivity.this, "Debe setear un monto", Toast.LENGTH_LONG).show();
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
                } catch (IOException | ISOException | NoSuchFieldException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
        return isoResponse;
    }

    /**
     * Cierra el teclado al seleccionar el boton generar
     */
    private void closeKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

    }

}