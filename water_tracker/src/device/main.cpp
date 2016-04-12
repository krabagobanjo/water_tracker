#include "mbed.h"
#include "FlowMeter.h"

// The bluetooth modules acts like a wireless UART. Thus,
// we communicate with it via a simple serial connection.
Serial bluefruit(p13, p14);

// PC serial connection for debugging.
Serial pc(USBTX, USBRX);

// This is the flow meter. It has a digital out line that
// goes high every time its pinwheel sensor spins. We only
// care about the absolute number of pulses, so we just need
// to increment a counter when we get an interrupt.
FlowMeter flow(p30);

DigitalOut enable_ble(p20);

// This enum defines the transmission states. The variable
// `state` defines the transmission state and is used by
static enum sync_state {
    SYNC_NONE,
    SYNC_START,
    SYNC_SENT,
    SYNC_ACKED,
} state;

#define SYNC_MSG_INIT 'S'
#define SYNC_MSG_FINISHED 'A'

#define MINIMUM_PULSE_THRESHOLD 10

// Handles receiving characters from the bluefruit. This will start
// and finish transmissions via the 'S' (start) and 'A' (ack) messages.
void bluefruit_rx_irq(void)
{
    // The received character.
    int recv = bluefruit.getc();
    switch (recv) {
    case SYNC_MSG_INIT:
        if (state == SYNC_NONE) {
            state = SYNC_START;
        }
        break;
    case SYNC_MSG_FINISHED:
        if (state == SYNC_SENT) {
            state = SYNC_ACKED;
        }
        break;
    default:
        break;
    }
}

// TODO: add an interrupt at midnight to save any unsynced water consumption
//       from the previous day. When next sync starts, send JSON with the
//       previous day's contents also.

// TODO: add some filtering so single pulses don't make it through.

// Sends a formatted measurement string to the bluetooth chip. A function
// here is useful level of abstraction if the message were ever to change
// (e.g. we wanted to use JSON)
static void send_measurement(Serial& ble, float measurement, char * units)
{
    ble.printf("%f %s\n\r", measurement, units);
}

int main(void)
{
    long n_pulses_sent = 0;
    long pulses_after_send = 0;

    bluefruit.attach(&bluefruit_rx_irq);
    enable_ble = 0;

    while (1) {
        // Core transmission state machine.
        switch (state) {
        case SYNC_START:
            n_pulses_sent = flow.get_pulse_count(); // Record this in case any pulses come in while sending.
            send_measurement(
                bluefruit,
                FlowMeter::pulses_to_fl_oz(n_pulses_sent + pulses_after_send),
                "fl oz");
            state = SYNC_SENT;
            break;
        case SYNC_SENT:
            // Wait for an ack from the phone.
            // This state is relevant for bluefruit_rx_irq.
            // TODO: start a timeout timer to reset ourselves to SYNC_NONE.
            break;
        case SYNC_ACKED:
            pulses_after_send = flow.get_pulse_count() - n_pulses_sent;
            flow.reset();
            state = SYNC_NONE;
        case SYNC_NONE:
        default:
            // Only turn on the bluetooth module if we have data.
            // Module should turn off almost immediately after sending data.
            enable_ble = (flow.get_pulse_count() + pulses_after_send) > MINIMUM_PULSE_THRESHOLD;
            break;
        }

    }
}
