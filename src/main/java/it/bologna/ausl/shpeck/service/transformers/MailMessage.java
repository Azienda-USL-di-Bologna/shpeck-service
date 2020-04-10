package it.bologna.ausl.shpeck.service.transformers;

/**
 *
 * @author Salo
 */
import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import it.bologna.ausl.model.entities.shpeck.Message.MessageType;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import java.text.ParseException;
import javax.mail.internet.MailDateFormat;

public class MailMessage implements MailIdentity {

    protected Address[] from, to, cc, reply_to;
    protected MimeMessage original;
    protected Boolean ispec = false;
    protected String subject, string_headers, id, raw_message, message = null;
    protected HashMap<String, String> headers;
    protected Date sendDate, receiveDate;
    protected Long providerUid;

    public MailMessage(MimeMessage m) throws MailMessageException {
        this.original = m;
        try {
            this.from = original.getFrom();
            this.to = original.getRecipients(Message.RecipientType.TO);
            this.cc = original.getRecipients(Message.RecipientType.CC);
            this.reply_to = original.getReplyTo();
            this.subject = original.getSubject();
            this.receiveDate = original.getReceivedDate();
            this.sendDate = original.getSentDate();
            this.id = MessageBuilder.defineMessageID(original);

        } catch (Exception ex) {
            throw new MailMessageException("Errore nella creazione del MailMessage", ex);
        }
    }

    public Address[] getCc() {
        return cc;
    }

    public Address[] getFrom() {
        return from;
    }

    public HashMap<String, String> getHeaders() {
        return headers;
    }

    public String getId() {
        return id;
    }

    public String getInReplyTo() {
        String replyArray[] = null;
        try {
            replyArray = original.getHeader("In-Reply-To");
        } catch (MessagingException e) {
        }
        if (replyArray != null) {
            return replyArray[0];
        }
        return null;
    }

    public Boolean getIsPec() {
        return ispec;
    }

    public String getMessage() throws MailMessageException {
        if (message != null) {
            return message;
        }
        try {
            message = getText(original);
        } catch (MessagingException ex) {
            throw new MailMessageException("errore nell'estrazione del testo del messaggio (MessagingException)", ex);
        } catch (IOException ex) {
            throw new MailMessageException("errore nell'estrazione del testo del messaggio (IOException)", ex);
        }
        return message;

    }

    public MimeMessage getOriginal() {
        return original;
    }

    public String getRaw_message() throws MailMessageException {

        if (raw_message == null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                original.writeTo(baos);
                raw_message = baos.toString("utf8");
                baos.reset();
            } catch (MessagingException ex) {
                throw new MailMessageException("errore nell'estrazione del testo del messaggio (MessagingException)", ex);
            } catch (IOException ex) {
                throw new MailMessageException("errore nell'estrazione del testo del messaggio (IOException)", ex);
            }
        }
        return raw_message;
    }

    public Date getReceiveDate() {
        return receiveDate;
    }

    public String[] getReferences() {
        try {
            return original.getHeader("References");
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public Address[] getReply_to() {
        return reply_to;
    }

    public Date getSendDate() {

        return sendDate;
    }

    public String getString_headers() {
        return string_headers;
    }

    public String getSubject() {
        return subject;
    }

    private String getText(Part p) throws MessagingException, IOException {
        if (p.isMimeType("text/*")) {
            String s = (String) p.getContent();
            return s;
        }

        if (p.isMimeType("multipart/alternative")) {
            // prefer html text over plain text
            Multipart mp = (Multipart) p.getContent();
            String text = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain")) {
                    if (text == null) {
                        text = getText(bp);
                    }
                    //continue;
                } else if (bp.isMimeType("text/html")) {
                    String s = getText(bp);
                    if (s != null) {
                        return s;
                    }
                } else {
                    return getText(bp);
                }
            }
            return text;
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getText(mp.getBodyPart(i));
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    public Address[] getTo() {
        return to;
    }

    public void setIsPec(Boolean ispec) {
        this.ispec = ispec;
    }

    public static MimeMessage getPecPayload(Part p) throws ShpeckServiceException {
        try {
            if (p.isMimeType("message/rfc822") && p.getFileName().equals("postacert.eml")) {
                return (MimeMessage) p.getContent();
            }
        } catch (MessagingException e) {
            throw new ShpeckServiceException("non possibile estrarre il payload pec", e);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (p.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) p.getContent();
                MimeMessage res = null;
                for (int i = 0; i < mp.getCount(); i++) {
                    res = getPecPayload(mp.getBodyPart(i));
                    if (res != null) {
                        return res;
                    }
                }
            }
        } catch (MessagingException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // TODO Auto-generated catch block
        return (MimeMessage) null;
    }

    public Long getProviderUid() {
        return providerUid;
    }

    public void setProviderUid(Long providerUid) {
        this.providerUid = providerUid;
    }

    @Override
    public MessageType getType() throws ShpeckServiceException {
        return MessageType.MAIL;
    }

    @Override
    public Object getMail() throws ShpeckServiceException {
        return this;
    }

    public static long getSendDateInGMT(MimeMessage mimeMessage) throws MessagingException {
        try {
            String originalDate = mimeMessage.getHeader("Date", null);
            MailDateFormat maildate = new MailDateFormat();
            Date parse = maildate.parse(originalDate);
            String[] split = originalDate.split(" ");
            
            if (split.length > 5) {
                switch (split[5]) {
                    case "CHAST":
                    case "UTC+12:45":
                        split[5] = "+1245";
                        break;
                    case "CHADT":
                    case "UTC+13:45":
                        split[5] = "+1345";
                        break;
                    case "GMT":
                    case "UTC":
                    case "UTC+00":
                    case "IBST":
                    case "UCT":
                    case "WET":
                    case "Z":
                    case "EGST":
                        split[5] = "+0000";
                        break;
                    case "UTC+01":
                    case "CET":
                    case "DFT":
                    case "IST":
                    case "MET":
                    case "WAT":
                    case "WEDT":
                    case "WEST":
                        split[5] = "+0100";
                        break;
                    case "CAT":
                    case "UTC+02":
                    case "CEDT":
                    case "CEST":
                    case "EET":
                    case "HAEC":
                    case "MEST":
                    case "SAST":
                    case "USZ1":
                    case "WAST":
                        split[5] = "+0200";
                        break;
                    case "AST":
                    case "UTC+03":
                    case "EAT":
                    case "EEDT":
                    case "EEST":
                    case "FET":
                    case "IDT":
                    case "IOT":
                    case "MSK":
                    case "SYOT":
                        split[5] = "+0300";
                        break;
                    case "IRST":
                    case "UTC+03:30":
                        split[5] = "+0330";
                        break;
                    case "AMT":
                    case "UTC+04":
                    case "AZT":
                    case "GET":
                    case "MUT":
                    case "RET":
                    case "SAMT":
                    case "SCT":
                    case "VOLT":
                        split[5] = "+0400";
                        break;

                    case "AFT":
                    case "UTC+04:30":
                    case "IRDT":
                        split[5] = "+0430";
                        break;
                    case "AMST":
                    case "UTC+05":
                    case "HMT":
                    case "MAWT":
                    case "MVT":
                    case "ORAT":
                    case "PKT":
                    case "TFT":
                    case "TJT":
                    case "TMT":
                    case "UZT":
                    case "YEKT":
                        split[5] = "+0500";
                        break;
                    case "UTC+05:30":
                    case "SLST":
                        split[5] = "+0530";
                        break;
                    case "NPT":
                    case "UTC+05:45":
                        split[5] = "+0545";
                        break;
                    case "UTC+06":
                    case "BIOT":
                    case "BST":
                    case "BTT":
                    case "KGT":
                    case "OMST":
                    case "VOST":
                        split[5] = "+0600";
                        break;
                    case "CCT":
                    case "UTC+06:30":
                    case "MMT":
                        split[5] = "+0630";
                        break;
                    case "CXT":
                    case "UTC+07":
                    case "DAVT":
                    case "HOVT":
                    case "ICT":
                    case "KRAT":
                    case "THA":
                    case "WIT":
                        split[5] = "+0700";
                        break;
                    case "ACT":
                    case "UTC+08":
                    case "AWST":
                    case "BDT":
                    case "CHOT":
                    case "CIT":
                    case "CST":
                    case "CT":
                    case "HKT":
                    case "IRKT":
                    case "MST":
                    case "MYT":
                    case "PST":
                    case "SGT":
                    case "SST":
                    case "ULAT":
                    case "WST":
                        split[5] = "+0800";
                        break;
                    case "CWST":
                    case "UTC+08:45":
                        split[5] = "+0845";
                        break;
                    case "AWDT":
                    case "UTC+09":
                    case "EIT":
                    case "JST":
                    case "KST":
                    case "TLT":
                    case "YAKT":
                        split[5] = "+0900";
                        break;
                    case "ACST":
                    case "UTC+09:30":
                        split[5] = "+0930";
                        break;
                    case "AEST":
                    case "UTC+10":
                    case "ChST":
                    case "CHUT":
                    case "DDUT":
                    case "EST":
                    case "PGT":
                    case "VLAT":
                        split[5] = "+1000";
                        break;
                    case "ACDT":
                    case "UTC+10:30":
                        split[5] = "+1030";
                        break;
                    case "AEDT":
                    case "UTC+11":
                    case "UTC+1100":
                    case "KOST":
                    case "LHST":
                    case "MIST":
                    case "NCT":
                    case "PONT":
                    case "SAKT":
                    case "SBT":
                    case "SRET":
                    case "VUT":
                    case "NFT":
                        split[5] = "+1100";
                        break;
                    case "FJT":
                    case "UTC+12":
                    case "GILT":
                    case "MAGT":
                    case "MHT":
                    case "NZST":
                    case "PETT":
                    case "TVT":
                    case "WAKT":
                        split[5] = "+1200";
                        break;
                    case "NZDT":
                    case "UTC+13":
                    case "PHOT":
                    case "TKT":
                    case "TOT":
                        split[5] = "+1300";
                        break;
                    case "LINT":
                    case "UTC+14":
                        split[5] = "+1400";
                        break;
                    case "CVT":
                    case "AZOST":
                    case "UTC-01":
                    case "EGT":
                        split[5] = "-0100";
                        break;
                    case "BRST":
                    case "UTC-02":
                    case "FNT":
                    case "GST":
                    case "PMDT":
                    case "UYST":
                        split[5] = "-0200";
                        break;
                    case "NDT":
                    case "UTC-02:30":
                        split[5] = "-0230";
                        break;
                    case "ADT":
                    case "UTC-03":
                    case "ART":
                    case "BRT":
                    case "CLST":
                    case "FKST":
                    case "GFT":
                    case "PMST":
                    case "PYST":
                    case "ROTT":
                    case "SRT":
                    case "UYT":
                        split[5] = "-0300";
                        break;
                    case "NST":
                    case "UTC-03:30":
                    case "NT":
                        split[5] = "-0330";
                        break;
                    case "UTC-04":
                    case "BOT":
                    case "CDT":
                    case "CLT":
                    case "COST":
                    case "EDT":
                    case "FKT":
                    case "GYT":
                    case "PYT":
                        split[5] = "-0400";
                        break;
                    case "VET":
                    case "UTC-04:30":
                        split[5] = "-0430";
                        break;
                    case "UTC-05":
                    case "EASST":
                    case "COT":
                    case "ECT":
                    case "PET":
                        split[5] = "-0500";
                        break;
                    case "UTC-06":
                    case "EAST":
                    case "GALT":
                    case "MDT":
                        split[5] = "-0600";
                        break;
                    case "UTC-07":
                    case "PDT":
                        split[5] = "-0700";
                        break;
                    case "AKDT":
                    case "UTC-08":
                    case "CIST":
                        split[5] = "-0800";
                        break;
                    case "AKST":
                    case "UTC-09":
                    case "GAMT":
                    case "GIT":
                    case "HADT":
                        split[5] = "-0900";
                        break;
                    case "MART":
                    case "UTC-09:30":
                    case "MIT":
                        split[5] = "-0930";
                        break;
                    case "CKT":
                    case "UTC-10":
                    case "HAST":
                    case "HST":
                    case "TAHT":
                        split[5] = "-1000";
                        break;
                    case "NUT":
                    case "UTC-11":
                        split[5] = "-1100";
                        break;
                    case "BIT":
                    case "UTC-12":
                        split[5] = "-1200";
                        break;
                }

                originalDate = String.join(" ", split);
                Date parse_secondo = maildate.parse(originalDate);
                return parse_secondo.getTime();

            }
            return parse.getTime();

        } catch (MessagingException | ParseException ex) {
        }
        return mimeMessage.getSentDate().getTime();

    }

}
