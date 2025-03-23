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
     * chreate a new Packet with a sequence field of "seq", an
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

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Add any necessary class variables here. Remember, you cannot use
    // these variables to send messages error free! They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

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
    private int dataPacketsDelivered; // num of data packets delivered to layer 5 from B
    private int ackPacketsSent; // num of ack packets sent from B
    private int corruptedPackets; // num of corrupted packets

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

    private int getChecksum(String data, int seq, int ack) {
        // calcs checksum using seq, ack, and data
        int checksum = seq + ack;
        for (char c : data.toCharArray()) {
            checksum += (int) c;
        }
        return checksum;
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send. It is firthe job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        // debug msg
        System.out.println("aOutput(): called");
        System.out.println("Upper Layer msg: " + message.getData());
        // check if window full
        if (nextSeqNum >= base + WindowSize) {
            System.out.println("Error: window es full, so packet not sent.");
            return;
        }

        // if not full then create packet based on the msg and next sequence number

        String data = message.getData();
        int seq = nextSeqNum;
        int ack = -1;
        int checksum = getChecksum(data, seq, ack);
        Packet packet = new Packet(seq, ack, checksum, data);

        // buffer the packet
        window[nextSeqNum % WindowSize] = packet;
        ackReceived[nextSeqNum % WindowSize] = false;

        // send packet to layer 3
        toLayer3(0, packet);
        originalPacketsSent++;

        // restart timer: stop current timer, and start timer for this packet
        stopTimer(0);
        startTimer(0, RxmtInterval);

        // go to next seq num
        nextSeqNum++;

    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side. "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet) {
        // debug print
        System.out.println("aInput(): called");
        // corrupt check
        int computedChecksum = getChecksum(packet.getPayload(), packet.getSeqnum(), packet.getAcknum());
        if (computedChecksum != packet.getChecksum()) {
            System.out.println("aInput(): got corrupted ACK/NAK");
            corruptedPackets++;
            return;
        }

        // check if packet is cumulative ack
        if (packet.getAcknum() >= base && packet.getAcknum() < nextSeqNum) {
            // slide window forward to acknowledged sequence number + 1
            base = packet.getAcknum() + 1;

            // mark all packets up to ACKed seq num as ack'd
            for (int i = base; i <= packet.getAcknum(); i++) {
                ackReceived[i % WindowSize] = true;
            }

            // restart timer if there are still unacknowledged packets in window
            // edit: ??
            if (base >= nextSeqNum) {
                stopTimer(0);
            }
        }

    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt() {
        // debug msg
        System.out.println("aTimerInterrupt(): called");
        // retransmit all unacked packets in window if timer runs out
        // edit: retransmit first unacked packet
        for (int i = base; i < nextSeqNum; i++) {
            if (!ackReceived[i % WindowSize]) {
                toLayer3(0, window[i % WindowSize]);
                System.out.println("aTimerInterrupt(): Retransmitting seqnum: " + window[i % WindowSize].getSeqnum()
                        + "  acknum: " + window[i % WindowSize].getAcknum() + "  checksum: "
                        + window[i % WindowSize].getChecksum()
                        + "  payload: " + window[i % WindowSize].getPayload());
                retransmissions++;
            }
            break;
        }

        // start the timer after retransmission, no need to stop bc already stopped
        startTimer(0, RxmtInterval);
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
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side. "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet) {

        // debug print
        System.out.println("bInput(): B getting " + packet.getPayload());

        // check if ack is correct
        // check if packet is corrupt
        // check buffer and see if incorrect ack can be buffered
        // send to layer5 if all is good

        // corrupt check
        int computedChecksum = getChecksum(packet.getPayload(), packet.getSeqnum(), packet.getAcknum());
        if (computedChecksum != packet.getChecksum()) {
            // System.out.println("bInput(): got corrupted ACK/NAK");
            corruptedPackets++;
            return;
        }
        System.out.println("bInput(): expecting pkt " + expectedSeqNum + ", getting pkt " + packet.getSeqnum());

        // check if packet is in expected window
        if (packet.getSeqnum() >= expectedSeqNum && packet.getSeqnum() < expectedSeqNum + WindowSize) {
            // buffer packet
            buffer[packet.getSeqnum() % WindowSize] = packet;

            // now deliver all in-order packets to layer 5
            while (buffer[expectedSeqNum % WindowSize] != null) {
                toLayer5(buffer[expectedSeqNum % WindowSize].getPayload());
                buffer[expectedSeqNum % WindowSize] = null;
                expectedSeqNum++;
                dataPacketsDelivered++;
            }

            // send a cumulative ACK for highest inorder sequence number
            String data = "";
            int seq = expectedSeqNum - 1;
            int ack = -1;
            int checksum = getChecksum(data, seq, ack);
            Packet ackPacket = new Packet(-1, expectedSeqNum - 1, checksum, data);
            toLayer3(1, ackPacket);
            ackPacketsSent++;
        } else if (packet.getSeqnum() < expectedSeqNum) {
            // dupe packet, drop and send cumulative ACK
            String data = "";
            int seq = expectedSeqNum - 1;
            int ack = -1;
            int checksum = getChecksum(data, seq, ack);
            Packet ackPacket = new Packet(-1, expectedSeqNum - 1, checksum, data);
            toLayer3(1, ackPacket);
            ackPacketsSent++;
        }
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
        System.out.println(
                "Ratio of lost packets:" + (double) (retransmissions - corruptedPackets) /
                        (originalPacketsSent + retransmissions + ackPacketsSent));
        System.out.println("Ratio of corrupted packets:" + (double) corruptedPackets /
                (originalPacketsSent + retransmissions + ackPacketsSent - (retransmissions - corruptedPackets)));
        System.out.println("Average RTT:" + "<YourVariableHere>");
        System.out.println("Average communication time:" + "<YourVariableHere>");
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        // System.out.println("Example statistic you want to check e.g. number of ACK
        // packets received by A :" + "<YourVariableHere>");
    }

}
