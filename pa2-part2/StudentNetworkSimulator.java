import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    /*
     * Predefined Constants (static member variables):
     *
     * int MAXDATASIZE : the maximum size of the Message data and
     * Packet payload
     *
     * int A : a predefined integer that represents entity A
     * int B : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     * void stopTimer(int entity):
     * Stops the timer running at "entity" [A or B]
     * void startTimer(int entity, double increment):
     * Starts a timer running at "entity" [A or B], which will expire in
     * "increment" time units, causing the interrupt handler to be
     * called. You should only call this with A.
     * void toLayer3(int callingEntity, Packet p)
     * Puts the packet "p" into the network from "callingEntity" [A or B]
     * void toLayer5(String dataSent)
     * Passes "dataSent" up to layer 5
     * double getTime()
     * Returns the current time in the simulator. Might be useful for
     * debugging.
     * int getTraceLevel()
     * Returns TraceLevel
     * void printEventList()
     * Prints the current event list to stdout. Might be useful for
     * debugging, but probably not.
     *
     *
     * Predefined Classes:
     *
     * Message: Used to encapsulate a message coming from layer 5
     * Constructor:
     * Message(String inputData):
     * creates a new Message containing "inputData"
     * Methods:
     * boolean setData(String inputData):
     * sets an existing Message's data to "inputData"
     * returns true on success, false otherwise
     * String getData():
     * returns the data contained in the message
     * Packet: Used to encapsulate a packet
     * Constructors:
     * Packet (Packet p):
     * creates a new Packet that is a copy of "p"
     * Packet (int seq, int ack, int check, String newPayload)
     * creates a new Packet with a sequence field of "seq", an
     * ack field of "ack", a checksum field of "check", and a
     * payload of "newPayload"
     * Packet (int seq, int ack, int check)
     * creates a new Packet with a sequence field of "seq", an
     * ack field of "ack", a checksum field of "check", and
     * an empty payload
     * Methods:
     * boolean setSeqnum(int n)
     * sets the Packet's sequence field to "n"
     * returns true on success, false otherwise
     * boolean setAcknum(int n)
     * sets the Packet's ack field to "n"
     * returns true on success, false otherwise
     * boolean setChecksum(int n)
     * sets the Packet's checksum to "n"
     * returns true on success, false otherwise
     * boolean setPayload(String newPayload)
     * sets the Packet's payload to "newPayload"
     * returns true on success, false otherwise
     * int getSeqnum()
     * returns the contents of the Packet's sequence field
     * int getAcknum()
     * returns the contents of the Packet's ack field
     * int getChecksum()
     * returns the checksum of the Packet
     * int getPayload()
     * returns the Packet's payload
     *
     */

    /*
     * Please use the following variables in your routines.
     * int WindowSize : the window size
     * double RxmtInterval : the retransmission timeout
     * int LimitSeqNo : when sequence number reaches this value, it wraps around
     */

    // for printing debug info
    private int lastAckReceived;
    private int ackCounter;

    // variables for A
    private int base; // seq num for oldest unack'd packet
    private int nextSeqNum; // seq num for next packet to be sent
    private Packet[] window; // buffer for packets in current window
    private boolean[] ackReceived; // tracking packets that have been ack'd

    // variables for B
    private int expectedSeqNum; // seq for next expected packet
    private Packet[] buffer; // buffer for out of order packets

    // variables for tracking statistics
    private int originalPacketsSent; // num of original packets sent from A
    private int retransmissions; // num of re-transmissions from A
    private int dataPacketsDelivered;// num of data packets delivered to layer 5 from B
    private int ackPacketsSent; // num of ack packets sent from B
    private int corruptedPackets; // num of corrupted packets
    private HashMap<Integer, Double> packetSendTimes; // maps seq nums to send time
    private double totalRTT; // sum of RTTs
    private int rttCount; // RTT count for dividing
    private HashMap<Integer, Double> packetSendTimes2; // same except includes retransmissions
    private double totalTime; // total time regardless of retransmit
    private int timeCount; // count for dividing for total time

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // This is the constructor. Don't touch!
    public StudentNetworkSimulator(int numMessages,
            double loss,
            double corrupt,
            double avgDelay,
            int trace,
            int seed,
            int winsize,
            double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize * 2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }

    // helper for compute checksum
    private int computeChecksum(String data, int seq, int ack) {
        int sum = seq + ack;
        for (char c : data.toCharArray()) {
            sum += c;
        }
        return sum;
    }

    // helper for updating RTT stats after receiving an ACK in aInput(),
    private void updateRTTStats(int ackNum) {
        // if ack in packetSendTimes or packetSendTimes2, remove it and update stats
        if (packetSendTimes.containsKey(ackNum)) {
            double rtt = getTime() - packetSendTimes.remove(ackNum);
            totalRTT += rtt;
            rttCount++;
        }
        if (packetSendTimes2.containsKey(ackNum)) {
            double tTime = getTime() - packetSendTimes2.remove(ackNum);
            totalTime += tTime;
            timeCount++;
        }
    }

    // helper for sliding window after ACK is received
    private void slideWindowAfterAck(int ackNum) {
        int oldBase = base;
        base = ackNum + 1;

        // mark all as acked, and set them to null ine window
        for (int i = oldBase; i <= ackNum; i++) {
            ackReceived[i % WindowSize] = true;
            window[i % WindowSize] = null; // remove from send buffer
        }

        // debugging stats
        int senderBufSize = 0;
        for (int i = 0; i < WindowSize; i++) {
            if (window[i] != null) {
                senderBufSize++;
            }
        }
        System.out.println("sender buffer size: " + senderBufSize);

        int indexNeedToSendNow = base;
        System.out.println("indexNeedToSendNow: " + indexNeedToSendNow);

        int diff = (ackNum - oldBase);
        System.out.println("differenceBetweenAckAndLastAck: " + diff);
    }

    // helper for processssing SACK array from an ACK in aInput()
    private void processSACK(int[] sackArr) {
        if (sackArr == null)
            return;
        for (int s : sackArr) {
            if (s >= base && s < nextSeqNum && !ackReceived[s % WindowSize]) {
                ackReceived[s % WindowSize] = true;
                window[s % WindowSize] = null;

                // remove from RTT stats
                if (packetSendTimes.containsKey(s)) {
                    double rtt = getTime() - packetSendTimes.remove(s);
                    totalRTT += rtt;
                    rttCount++;
                }
                if (packetSendTimes2.containsKey(s)) {
                    double tTime = getTime() - packetSendTimes2.remove(s);
                    totalTime += tTime;
                    timeCount++;
                }
            }
        }
    }

    // helper for building SACK and sending ACK from B
    private void sendAckWithSack() {
        // cumulative ACK is for the last in-order packet
        int cumAck = expectedSeqNum - 1;
        String ackData = "";
        int[] sackList = new int[5];
        Arrays.fill(sackList, -1);

        int idx = 0;
        for (int s = expectedSeqNum; s < expectedSeqNum + WindowSize; s++) {
            if (buffer[s % WindowSize] != null) {
                sackList[idx++] = s;
                if (idx == 5)
                    break;
            }
        }

        System.out.println("B sent SACK:" + Arrays.toString(sackList));

        int ackSum = computeChecksum(ackData, cumAck, -1);
        Packet ackPkt = new Packet(-1, cumAck, ackSum, ackData);
        ackPkt.setSack(sackList);

        toLayer3(B, ackPkt);
        ackPacketsSent++;
    }

    // helper for printing A-side send buffer
    private String printSendBuffer() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < WindowSize; i++) {
            if (window[i] != null) {
                sb.append("seqnum: ").append(window[i].getSeqnum())
                        .append("  acknum: ").append(window[i].getAcknum())
                        .append("  checksum: ").append(window[i].getChecksum())
                        .append("  payload: ").append(window[i].getPayload())
                        .append("  sack: ").append(window[i].getSack())
                        .append("; ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // helper for printing B-side buffer (for debugging)
    private String printReceiveBuffer() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] != null) {
                sb.append(i).append("=");
                Packet p = buffer[i];
                sb.append("seqnum: ").append(p.getSeqnum())
                        .append("  acknum: ").append(p.getAcknum())
                        .append("  checksum: ").append(p.getChecksum())
                        .append("  payload: ").append(p.getPayload())
                        .append("  sack: ").append(p.getSack())
                        .append("; ");
            }
        }
        sb.append("}");
        return sb.toString();

    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send. It is firthe job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        // if window full, do not send
        if (nextSeqNum >= base + WindowSize) {
            System.out.println("Error: window es full, so packet not sent.");
            return;
        }

        // build packet
        String data = message.getData();
        int seq = nextSeqNum;
        int ack = -1;
        int chksum = computeChecksum(data, seq, ack);

        Packet p = new Packet(seq, ack, chksum, data);

        // put in send window
        window[seq % WindowSize] = p;
        ackReceived[seq % WindowSize] = false;

        // start/stop timer
        stopTimer(A);
        startTimer(A, RxmtInterval);

        // send to layer3
        toLayer3(A, p);
        originalPacketsSent++;

        // record time
        packetSendTimes.put(seq, getTime());
        packetSendTimes2.put(seq, getTime());

        nextSeqNum++;
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side. "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet) {
        System.out.println("A input called");
        System.out.println("LastACKReceived:" + lastAckReceived);
        System.out.println("LastPacketSent:" + (nextSeqNum - 1));

        // print send buffer
        System.out.println("print send buffer:" + printSendBuffer());

        // show incoming packet
        System.out.println("A gets a packet:seqnum: " + packet.getSeqnum()
                + "  acknum: " + packet.getAcknum()
                + "  checksum: " + packet.getChecksum()
                + "  payload: " + packet.getPayload()
                + "  sack: " + packet.getSack());

        // also show SACK contents
        System.out.println("sack:" + Arrays.toString(packet.getSack()));

        // checksum check
        int computed = computeChecksum(packet.getPayload(),
                packet.getSeqnum(),
                packet.getAcknum());
        if (computed != packet.getChecksum()) {
            System.out.println("A receive a packet, the packet is corrupted!");
            System.out.println("The previous checksum is " + packet.getChecksum());
            System.out.println("The now checksum is " + computed);
            corruptedPackets++;
            return;
        }

        // correct ACK
        int ackNum = packet.getAcknum();
        System.out.println(ackCounter + "A gets a correct ACK from B, the sequence number is :" + ackNum);
        ackCounter++;

        // update lastAckReceived
        lastAckReceived = ackNum;

        // process ack if in range
        if (ackNum >= base && ackNum < nextSeqNum) {
            updateRTTStats(ackNum); // remove from RTT tracking
            slideWindowAfterAck(ackNum); // slide base, mark acked
        }

        // process any SACK entries
        processSACK(packet.getSack());

        // show updated buffer
        System.out.println("New send buffer: " + printSendBuffer());

        // if everything acked, mention empty
        boolean empty = true;
        for (int i = base; i < nextSeqNum; i++) {
            if (!ackReceived[i % WindowSize]) {
                empty = false;
                break;
            }
        }
        if (empty) {
            System.out.println("send buffer is empty");
        }

        // if no unacked left, stop timer
        if (base >= nextSeqNum) {
            stopTimer(A);
        }
    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt() {
        System.out.println("Interrupt called");
        stopTimer(A);

        // only retransmit the earliest unACKed packet (gbn)
        for (int i = base; i < nextSeqNum; i++) {
            if (!ackReceived[i % WindowSize]) {
                // remove from RTT
                packetSendTimes.remove(window[i % WindowSize].getSeqnum());
                toLayer3(A, window[i % WindowSize]);
                retransmissions++;
                break;
            }
        }
        startTimer(A, RxmtInterval);
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        base = FirstSeqNo;
        nextSeqNum = FirstSeqNo;
        window = new Packet[WindowSize];
        ackReceived = new boolean[WindowSize];
        originalPacketsSent = 0;
        retransmissions = 0;

        lastAckReceived = -1;
        ackCounter = 1;

        packetSendTimes = new HashMap<>();
        totalRTT = 0.0;
        rttCount = 0;

        packetSendTimes2 = new HashMap<>();
        totalTime = 0.0;
        timeCount = 0;
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side. "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet) {
        int check = computeChecksum(packet.getPayload(),
                packet.getSeqnum(),
                packet.getAcknum());
        if (check != packet.getChecksum()) {
            System.out.println("B receive a packet, the packet is corrupt!");
            corruptedPackets++;
            return;
        }

        int seqnum = packet.getSeqnum();

        // distinguish older-dup, in-order, out-of-order
        if (seqnum < expectedSeqNum) {
            System.out.println("B receive a a out of range packet from A, the sequence number is :" + seqnum);
            System.out.println("The packet is acked before");
        } else if (seqnum == expectedSeqNum) {
            System.out.println("B receive a correct packet from A, the sequence number is :" + seqnum);
            System.out.println("receiveBuffer: " + printReceiveBuffer());
            System.out.println("the next packet expected is " + (expectedSeqNum + 1));

            toLayer5(packet.getPayload());
            dataPacketsDelivered++;
            expectedSeqNum++;

            // deliver in-order from buffer
            while (buffer[expectedSeqNum % WindowSize] != null) {
                toLayer5(buffer[expectedSeqNum % WindowSize].getPayload());
                buffer[expectedSeqNum % WindowSize] = null;
                dataPacketsDelivered++;
                expectedSeqNum++;
            }
        } else {
            // out-of-order
            System.out.println("B get a out of range packet from A. The payload is " + packet.getPayload()
                    + "\nthe sequence number is " + seqnum);
            System.out.println("the next packet expected is " + expectedSeqNum);
            System.out.println("add it into sack");
            buffer[seqnum % WindowSize] = packet;
        }

        // bulid & send ACK with SACK
        sendAckWithSack();
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
        expectedSeqNum = FirstSeqNo;
        buffer = new Packet[WindowSize];
        dataPacketsDelivered = 0;
        ackPacketsSent = 0;
    }

    // Use to print final statistics
    protected void Simulation_done() {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO
        // NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + originalPacketsSent);
        System.out.println("Number of retransmissions by A:" + retransmissions);
        System.out.println("Number of data packets delivered to layer 5 at B:" + dataPacketsDelivered);
        System.out.println("Number of ACK packets sent by B:" + ackPacketsSent);
        System.out.println("Number of corrupted packets:" + corruptedPackets);
        System.out.println("Ratio of lost packets:"
                + (double) (retransmissions - corruptedPackets)
                        / (originalPacketsSent + retransmissions + ackPacketsSent));
        System.out.println("Ratio of corrupted packets:"
                + (double) corruptedPackets
                        / (originalPacketsSent + retransmissions + ackPacketsSent
                                - (retransmissions - corruptedPackets)));
        System.out.println("Average RTT:"
                + ((rttCount > 0) ? (totalRTT / rttCount) : 0.0));
        System.out.println("Average communication time:"
                + ((timeCount > 0) ? (totalTime / timeCount) : 0.0));
        System.out.println("==================================================");

        System.out.println("\nEXTRA:");

    }
}