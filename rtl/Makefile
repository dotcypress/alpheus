PROJ = Top

all: elaborate build/$(PROJ).rpt build/$(PROJ).bin

%.blif: %.v
	yosys -p 'synth_ice40 -top $(PROJ) -blif $@' build/PLLBlackBox.v $<

%.asc: src/main/resources/pins.pcf %.blif
	arachne-pnr -d 8k -P cm81 -o $@ -p $^

%.bin: %.asc
	icepack $< $@

%.rpt: %.asc
	icetime -d lp8k -mtr $@ $<

%_syn.v: %.blif
	yosys -p 'read_blif -wideports $^; write_verilog $@'

elaborate:
	sbt "runMain build.Elaborate"

flash: build/$(PROJ).bin
	tinyprog -p $<

clean:
	rm -f build

.SECONDARY:
.PHONY: all elaborate flash clean
