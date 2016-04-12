#ifndef FLOW_METER_H
#define FLOW_METER_H

#include "mbed.h"

class FlowMeter {
public:
    /*
     * Makes a FlowMeter object with the pin as output to the mbed.
     */
    FlowMeter(PinName out);

    /*
     * Utility functions for converting pulses to volumetric measures.
     */
    static float pulses_to_fl_oz(int pulses);
    static float pulses_to_ml(int pulses);

    /*
     * Reads the number of pulses.
     *
     * @return number of pulses recorded.
     */
    long get_pulse_count(void);

    /*
     * Convenience functions to get volumetric measures without
     * called a conversion function.
     */
    float get_fl_oz(void);
    float get_ml(void);

    /*
     * Resets the pulse count.
     */
    void reset(void);

private:
    void handle_pulse_irq(void);
    volatile long _pulses;
    InterruptIn _pin;


};

#endif
