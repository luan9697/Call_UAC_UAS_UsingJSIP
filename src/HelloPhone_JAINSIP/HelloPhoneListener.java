/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HelloPhone_JAINSIP;

import com.sun.jmx.snmp.BerDecoder;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.swing.JOptionPane;

/**
 *
 * @author KhangDang
 */
public class HelloPhoneListener implements SipListener {

	//tao cac bien khoi dong SIP
    private SipFactory sipFactory;
    private SipStack sipStack;
    private ListeningPoint listeningPoint;
    private SipProvider sipProvider;
    private MessageFactory messageFactory;
    private HeaderFactory headerFactory;
    private AddressFactory addressFactory;
    private ContactHeader contactHeader;

  //cac bien thuc hien giao dich SIP
    private ClientTransaction clientTransaction;
    private ServerTransaction serverTransaction;

    private boolean isUAS = false;// UAC, = true -> UAS
    private boolean isACK = false;// UAC chua gui ACK, = true -> da gui.

    private String sIP; //localhosst address cua may goi di.
    private int iSipPort; // post cho giao thuc SIP cua may goi di
    private HelloPhoneGUI GUI;


    private SdpTool sdpOffer; // tao SDP message tu UAC

    private SdpTool sdpAnswer; // tao SDP message tu UAS

    // nhac chuong phia UAC
    RingTool ringClient;

    //nhac chuong phia UAS
    RingTool ringServer;

    //tinh thoi gian. neu UAC cho ket noi tu UAS ma qua 30s se ket thuc viec thuc hien ket noi
    Timer timerS;

    // voiceClient UAC -> UAS
    VoiceTool voiceClient;
    // voiceServer UAS -> UAC
    VoiceTool voiceServer;

    public HelloPhoneListener(HelloPhoneGUI gui) { // khoi dong giao thuc SIP
        try {
            GUI = gui;
            sIP = "192.168.56.1";
            iSipPort = GUI.getSipPort();

            sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");

            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", "myStack");
            sipStack = sipFactory.createSipStack(properties);

            messageFactory = sipFactory.createMessageFactory();
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();

            listeningPoint = sipStack.createListeningPoint(sIP, iSipPort, "udp");
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);

            Address contactAddress = addressFactory.createAddress("sip:" + sIP + ":" + iSipPort);
            contactHeader = headerFactory.createContactHeader(contactAddress);

            // sdpOffer va  sdpAnswer
            sdpOffer = new SdpTool();
            sdpAnswer = new SdpTool();

            // ringClient va ringServer
            ringClient = new RingTool();
            ringServer = new RingTool();

            // voiceClient va  voiceServer
            voiceClient = new VoiceTool();
            voiceServer = new VoiceTool();

            GUI.setInit("Init : " + sIP + ":" + iSipPort);

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public boolean isUAS() {
        return isUAS;
    }

    public void disconnect() {
        try {
            sipProvider.removeSipListener(this);
            sipProvider.removeListeningPoint(listeningPoint);
            sipStack.deleteListeningPoint(listeningPoint);
            sipStack.deleteSipProvider(sipProvider);

            GUI.clean();

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public void sendRequest() { //tao ra INVITE request gui tu UAC den cho UAS
        try {
            Address toAddress = addressFactory.createAddress(GUI.getDestination());
            ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

            Address fromAddress = addressFactory.createAddress("sip:" + sIP + ":" + iSipPort);
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "564385");

            ViaHeader viaHeader = headerFactory.createViaHeader(sIP, iSipPort, "udp", null);
            ArrayList viaHeaders = new ArrayList();
            viaHeaders.add(viaHeader);

            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(20);

            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, "INVITE");

            CallIdHeader callIdHeader = sipProvider.getNewCallId();

            URI requestUri = toAddress.getURI();
            Request request = messageFactory.createRequest(requestUri, "INVITE",
                    callIdHeader, cSeqHeader, fromHeader, toHeader,
                    viaHeaders, maxForwardsHeader);
            request.addHeader(contactHeader);

            // tao SDP message va luu vao message body cua ban tin INVITE
            SdpInfo senderInfo_UAC = new SdpInfo();//chua cac thong tin thuc hien voice chat qua UAC
            senderInfo_UAC.setIpSender(sIP);
            senderInfo_UAC.setVoicePort(GUI.getVoicePort());
            senderInfo_UAC.setVoiceFormat(0);

            // 
            ContentTypeHeader myContentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
            byte[] content = sdpOffer.createSdp(senderInfo_UAC);
            request.setContent(content, myContentTypeHeader); //luu SDP message vao message body

            clientTransaction = sipProvider.getNewClientTransaction(request);
            clientTransaction.sendRequest();

            // play nhac chuong phia UAC
            ringClient.playRing("file://C:\\Users\\15510\\Downloads\\ringclient.mp2");

            GUI.Display("Send : " + request.toString());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public void terminateRequest() { //Huy cuoc goi khi UAC chua gui ACK de thiet lap cuoc goi || BYE de ket thuc cuoc goi da duoc thiet lap.
        try {
            if (!isACK) { // ACK = true --> cuoc goi chua duoc thiet lap
                // UAC tao va gui CANCEL request 
                Request cancelRequest = clientTransaction.createCancel();
                ClientTransaction cancelClientTransaction
                        = sipProvider.getNewClientTransaction(cancelRequest);
                cancelClientTransaction.sendRequest();

                // UAC huy cuoc gọi dang do chuong 
                ringClient.stopRing();

                GUI.Display("Send : " + cancelRequest.toString());

            } else {
                // UAC tao BYE request
                Request byeRequest
                        = clientTransaction.getDialog().createRequest(Request.BYE);
                // bo sung contact header vao BYE
                byeRequest.addHeader(contactHeader);
                // tao 1 ClientTransaction moi danh cho BYE request
                ClientTransaction byeClientTransaction
                        = sipProvider.getNewClientTransaction(byeRequest);

                clientTransaction.getDialog().sendRequest(byeClientTransaction);
                GUI.Display("Send : " + byeRequest.toString());

                isACK = false;

                // key thuc voice chat phia UAC
                voiceClient.stopMedia();
            }

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public void sendResponse() {  // khi UE2 bam YES de nhan cuoc goi
        try {
            // lay INVITE request tu serverTransaction
            Request request = serverTransaction.getRequest();

            //tao ra 200 OK response
            Response response = messageFactory.createResponse(200, request);
            response.addHeader(contactHeader);

            // lay SDP message trong message body cua INVITE
            byte[] cont = (byte[]) request.getContent();
            // lay cac thong tin trong SDP message 
            sdpAnswer.getSdp(cont);

            // senderInfo_UAS : chứa các thông tin thuc hien voice chat qua UAS
            SdpInfo senderInfo_UAS = new SdpInfo();
            senderInfo_UAS.setIpSender(sIP);
            senderInfo_UAS.setVoicePort(GUI.getVoicePort());
            senderInfo_UAS.setVoiceFormat(0);

            // dinh nghia lai noi  dung danh cho message body cua 200 OK response
         
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
            // tao SDP message va luu cac thong tin vao senderInfo cua sdpAnswer
            byte[] myContent = sdpAnswer.createSdp(senderInfo_UAS);
            // luu SDP message vao message body cua 200 OK response
            response.setContent(myContent, contentTypeHeader);

            // gui response
            serverTransaction.sendResponse(response);

            // Stop nhac chuong va chuan bị thuc hien  voice chat
            ringServer.stopRing();

            timerS.cancel();

            // thuc hien voice chat phia UAS :
            voiceServer.senderInfo(sdpAnswer.getSenderInfo());
            voiceServer.receiverInfo(sdpAnswer.getReceiverInfo());
            // khoi tao Session
            voiceServer.init();
            voiceServer.startMedia();
            // gui send stream
            voiceServer.send();

            GUI.Display("Send : " + response.toString());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public void terminateResponse() { //tu choi cuoc goi || ket thuc cuoc goi
        try {
            if (!isACK) {
                // lay INVITE request tu serverTransaction
                Request request = serverTransaction.getRequest();

                // tao "478 Termnated" response va gui UAC
                Response response = messageFactory.createResponse(487, request);
                serverTransaction.sendResponse(response);

                GUI.Display("Send : " + response.toString());

                ringServer.stopRing();
                timerS.cancel();
            } else {
                // UAS tao BYE request va ket thuc cuoc goi
                Request byeRequest
                        = serverTransaction.getDialog().createRequest("BYE");
                byeRequest.addHeader(contactHeader);

                ClientTransaction byeclientTransaction
                        = sipProvider.getNewClientTransaction(byeRequest);
               
                serverTransaction.getDialog().sendRequest(byeclientTransaction);

                GUI.Display("Send : " + byeRequest.toString());

                isACK = false;

                voiceServer.stopMedia();
            }

            isUAS = false;

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    // qua trinh nhan REQUEST duoc gui tu UAS:
    //UAS nhan duoc INVITE tu UAC, tu dong tao ta 180RING (Nhan duoc INVITE va dang cho xu li) gui den UAC
    //UAS nhan duoc CANCEL request, tu dong tao ra 487 de huy cuoc goi INVITE va 200 OK respond de hoan thanh CANCEL request
    @Override
    public void processRequest(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            GUI.Display("Received : " + request.toString());

            if (request.getMethod().equals(Request.INVITE)) {
                isUAS = true;
                // tao 180 RINGING                
                Response response = messageFactory.createResponse(180, request);
                response.addHeader(contactHeader);
                ToHeader toHeader = (ToHeader) response.getHeader("To");
                toHeader.setTag("45432678");

                serverTransaction = sipProvider.getNewServerTransaction(request);
                serverTransaction.sendResponse(response);

                ringServer.playRing("file://C:\\Users\\15510\\Downloads\\RingServer.wav");

                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        HelloPhoneListener.this.terminateResponse();
                    }
                };
                timerS = new Timer("UAS khong phan hoi");

                timerS.schedule(task, 30000);

                GUI.Display("send : " + response.toString());
            }

            if (request.getMethod().equals(Request.CANCEL)) {
                Request inviteReq = serverTransaction.getRequest();

                Response response = messageFactory.createResponse(487, inviteReq);
                serverTransaction.sendResponse(response);
                GUI.Display("send : " + response.toString());

                // tao "200 OK" response danh cho CANCEL           
                Response cancelResponse = messageFactory.createResponse(200, request);
                
                ServerTransaction cancelServerTransaction = requestEvent.getServerTransaction();
                cancelServerTransaction.sendResponse(cancelResponse);
                GUI.Display("send : " + cancelResponse.toString());

                ringServer.stopRing();
                JOptionPane.showMessageDialog(GUI, "UAC Ä‘Ã£ há»§y cuá»™c gá»�i !");
 
                timerS.cancel();

                isUAS = false;
            }

            if (request.getMethod().equals(Request.ACK)) {
                isACK = true;
            }

            if (request.getMethod().equals(Request.BYE)) {
                // tao"200 OK" response danh cho BYE request
                Response response = messageFactory.createResponse(200, request);
                response.addHeader(contactHeader);
                ServerTransaction byeServerTransaction = requestEvent.getServerTransaction();
                byeServerTransaction.sendResponse(response);

                if (!isUAS) {
                    voiceClient.stopMedia();
                    JOptionPane.showMessageDialog(GUI, "UAS ket thuc cuoc goi !");
                } 
                
                if(isUAS){
                    voiceServer.stopMedia();
                    JOptionPane.showMessageDialog(GUI, "UAC ket thuc cuoc goi !");
                }

                GUI.Display("Send : " + response.toString());
                isUAS = false;
                isACK = false;
            }

        } catch (Exception ex) {
            System.out.println("processRequest : " + ex.getMessage());
        }
    }

    //UAC nhan respond tu UAS, khi nhan duoc 200 OK respond danh cho INVITE resquest thi UAC gui ACK request
    @Override
    public void processResponse(ResponseEvent responseEvent ) {
        try {
            Response response = responseEvent.getResponse();
            GUI.Display("Received : " + response.toString());
            CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

            if ((cSeqHeader.getMethod().equals(Request.INVITE))
                    && (response.getStatusCode() == 200)) {

                // lay SDP message trong message body cua 200 OK
                byte[] content = (byte[]) response.getContent();
                // lay cac thong tin trong SDP message 
                sdpOffer.getSdp(content);

                long numseq = cSeqHeader.getSeqNumber();
                Request ACK = clientTransaction.getDialog().createAck(numseq);
                ACK.addHeader(contactHeader);
                clientTransaction.getDialog().sendAck(ACK);

                ringClient.stopRing();

                isACK = true;

                // thuc hien voice chat phia UAC :
                voiceClient.senderInfo(sdpOffer.getSenderInfo());
                voiceClient.receiverInfo(sdpOffer.getReceiverInfo());
                // khoi tao Session
                voiceClient.startMedia();
                // gui send stream
                voiceClient.send();

                GUI.Display("Send : " + ACK.toString());
            }

            if (response.getStatusCode() == 487) {
                ringClient.stopRing();
                JOptionPane.showMessageDialog(GUI, "UAS Ä‘Ã£ tá»« chá»‘i cuá»™c gá»�i !");
            }

        } catch (Exception ex) {
            System.out.println("processResponse : " + ex.getMessage());
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent
    ) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent
    ) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
    	
    	ClientTransaction clientTransaction = transactionTerminatedEvent.getClientTransaction();
        //   System.out.println("ClientTrasaction Terminated : " + clientTransaction.getRequest());
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        Dialog dialog = dialogTerminatedEvent.getDialog();
        //   System.out.println("Dialog Terminated : " + dialog);
    }

}
