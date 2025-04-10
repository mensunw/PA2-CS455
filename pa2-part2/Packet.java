public class Packet {
    private int seqnum;
    private int acknum;
    private int checksum;
    private String payload;
    private int[] sack;

    public Packet(Packet p) {
        seqnum = p.getSeqnum();
        acknum = p.getAcknum();
        checksum = p.getChecksum();
        payload = new String(p.getPayload());

        sack = new int[5];
    }

    public Packet(int seq, int ack, int check, String newPayload) {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        sack = new int[5];
        if (newPayload == null) {
            payload = "";
        } else if (newPayload.length() > NetworkSimulator.MAXDATASIZE) {
            payload = null;
        } else {
            payload = new String(newPayload);
        }
    }

    public Packet(int seq, int ack, int check) {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        payload = "";
        sack = new int[5];
    }

    public boolean setSeqnum(int n) {
        seqnum = n;
        return true;
    }

    public boolean setAcknum(int n) {
        acknum = n;
        return true;
    }

    public boolean setChecksum(int n) {
        checksum = n;
        return true;
    }

    public boolean setPayload(String newPayload) {
        if (newPayload == null) {
            payload = "";
            return false;
        } else if (newPayload.length() > NetworkSimulator.MAXDATASIZE) {
            payload = "";
            return false;
        } else {
            payload = new String(newPayload);
            return true;
        }
    }

    public int getSeqnum() {
        return seqnum;
    }

    public int getAcknum() {
        return acknum;
    }

    public int getChecksum() {
        return checksum;
    }

    public String getPayload() {
        return payload;
    }

    public int[] getSack() {
        // for getting sack
        return sack;
    }

    public void setSack(int[] newSack) {
        // set the sack

        sack = newSack;
    }

    public String toString() {
        return ("seqnum: " + seqnum + "  acknum: " + acknum + "  checksum: " +
                checksum + "  payload: " + payload + " sack: ");
    }

}
