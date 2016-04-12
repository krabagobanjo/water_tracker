#include "FlowMeter.h"

void FlowMeter::handle_pulse_irq(void)
{
    _pulses++;
}

FlowMeter::FlowMeter(PinName out) : _pin(out)
{
    _pulses = 0;
    _pin.rise(this, &FlowMeter::handle_pulse_irq);

}

long FlowMeter::get_pulse_count(void)
{
    return _pulses;
}

float FlowMeter::get_fl_oz(void)
{
    return pulses_to_fl_oz(_pulses);
}

float FlowMeter::get_ml(void)
{
    return pulses_to_ml(_pulses);
}

float FlowMeter::pulses_to_ml(int pulses)
{
    return ((float)pulses) / 2.2222222f; // pulses per mL.
}

float FlowMeter::pulses_to_fl_oz(int pulses)
{
    return ((float)pulses) / 13.308294f; // pulses per fl oz.
}


void FlowMeter::reset(void)
{
    _pulses = 0;
}
