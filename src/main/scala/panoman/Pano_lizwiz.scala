package panoman

import spinal.core._
import spinal.lib.Counter
import spinal.lib.CounterFreeRun
import spinal.lib.GrayCounter
import spinal.lib.master
import spinal.lib.io.ReadableOpenDrain


case class Pano() extends Component {

    val io = new Bundle {
        val osc_clk             = in(Bool)

        val pano_button         = in(Bool)

        val vo_clk              = out(Bool)
        val vo                  = out(VgaData())

        val led_green           = in(Bool)
        val led_blue            = in(Bool)
        val led_red             = out(Bool)

        val vo_scl = master(ReadableOpenDrain(Bool))
        val vo_sda = master(ReadableOpenDrain(Bool))

        val audio_scl = master(ReadableOpenDrain(Bool))
        val audio_sda = master(ReadableOpenDrain(Bool))

        var audio_mclk = out(Bool)
        var audio_bclk = out(Bool)
        var audio_dacdat = out(Bool)
        var audio_daclrc = out(Bool)
        var audio_adcdat = in(Bool)
        var audio_adclrc = out(Bool)
    }
    noIoPrefix()

    val gpio_out = Bits(18 bits)

    //============================================================
    // Create pacman clock domain
    // We need 6.144 Mhz (1x), 12.288 (x2), and 24.576 (x4)
    // We'll multiple the 100 Mhz input clock by 8 and divide
    // by 5 to generated a 160 Mhz clock which we will feed into a
    // second DCM which will divide it by 6.5 to generate 24.615 Mhz.  
    // We'll divide 24.615 by 2 to generate 12.31 and by 4 to 
    // generate 6.15 Mhz which is .16 percent faster than ideal. 
    //============================================================

    val pacman_clk = new Pacman_clk()
    pacman_clk.io.CLKIN_IN <> io.osc_clk

    val pacman_clk1 = new Pacman_clk1()
    pacman_clk1.io.CLKIN_IN <> pacman_clk.io.CLKFX_OUT


    //============================================================
    // Create pacman clock domain
    //============================================================
    val pacmanClockDomain = ClockDomain(
        clock = pacman_clk1.io.CLKDV_OUT,
        frequency = FixedFrequency(24.576 MHz),
        config = ClockDomainConfig(
                    resetKind = BOOT
        )
    )

    val core = new ClockingArea(pacmanClockDomain) {
    // Create div2 and div4 clocks
        var clk_cntr6 = Reg(UInt(2 bits)) init(0)
        clk_cntr6 := clk_cntr6 + 1
        var clk12  = RegNext(clk_cntr6(0))
        var clk6  = RegNext(clk_cntr6(0) & ~clk_cntr6(1))

        val reset_unbuffered_ = True

        val reset_cntr = Reg(UInt(5 bits)) init(0)
        when(reset_cntr =/= U(reset_cntr.range -> true)){
            reset_cntr := reset_cntr + 1
            reset_unbuffered_ := False
        }

        val reset_ = RegNext(reset_unbuffered_)
        val u_pano_core = new PanoCore()
        io.audio_scl <> u_pano_core.io.codec_scl
        io.audio_sda <> u_pano_core.io.codec_sda
        io.vo_scl <> u_pano_core.io.vo_scl
        io.vo_sda <> u_pano_core.io.vo_sda
        io.led_red := u_pano_core.io.led1
        gpio_out := u_pano_core.io.gpio_out
    }

    var audio = new audio()
    audio.io.clk12 <> core.clk12
    audio.io.reset12_ := core.reset_
    audio.io.audio_mclk <> io.audio_mclk
    audio.io.audio_bclk <> io.audio_bclk
    audio.io.audio_dacdat <> io.audio_dacdat
    audio.io.audio_adcdat <> io.audio_adcdat
    audio.io.audio_daclrc <> io.audio_daclrc
    audio.io.audio_adclrc <> io.audio_adclrc

    var pacman = new Pacman()
    io.vo_clk <> pacman_clk.io.CLKFX_OUT
    pacman.io.clk <> pacman_clk1.io.CLKDV_OUT
    pacman.io.ena_12 <> core.clk12
    pacman.io.ena_6 <> core.clk6
    pacman.io.I_JOYSTICK_B := 31

    when(gpio_out(14)) {
        audio.io.audio_sample := S(pacman.io.O_AUDIO << 6)
    }
    .otherwise{
        audio.io.audio_sample := 0
    }

    // pacman.io.I_SW bits:
    //      7 - character set ?
    //      6 - difficulty ?
    //      5,4:  00 - bonus pacman at 10K
    //      5,4:  01 - bonus pacman at 15K
    //      5,4:  10 - bonus pacman at 20K
    //      5,4:  11 - no bonus
    //      
    //      3,2: 00 - 1 pacman
    //      3,2: 01 - 2 pacmen
    //      3,2: 10 - 3 pacmen
    //      3,2: 11 - 5 pacmen
    //
    //      1,0: 00 - free play
    //      1,0: 01 - 1 coin, 1 play
    //      1,0: 10 - 1 coin, 2 play
    //      1,0: 11 - 2 coin, 1 play

    // gpio_out bits:
    //  -   17 - Initialization complete
    //  -   16 - MCP23017 present
    // PB7  15 - P2 LED1
    // PB6  14 - P2 SW2     - audio mute
    // PB5  13 - P2 SW1     - 0 (on): cocktail, 1: upright
    // PB4  12 - P2 up
    // PB3  11 - P2 trigger - 2 player start
    // PB2  10 - P2 down
    // PB1  9  - P2 left
    // PB0  8  - P2 right
    //
    // PA7  7  - P1 LED1
    // PA6  6  - P1 SW2
    // PA5  5  - P1 SW1     
    // PA4  4  - P1 up
    // PA3  3  - P1 trigger - 1 player start
    // PA2  2  - P1 down
    // PA1  1  - P1 left
    // PA0  0  - P1 right


    // VGA_I2C pushbutton mapping **bzboi**
    // REF  SCH    ARCADE    MCP23017
    // SW1 (sw1_2) P2-START  gpio(13)
    // SW2 (sw2_2) TBD       TBD
    // SW4 (sw2)   COIN      gpio(6)
    // SW3 (sw1)   P1-START  gpio(5)
    //
    // IN0 lizwiz
    //      0 - p1 up         gpio(4)
    //      1 - p1 left       gpio(1)
    //      2 - p1 right      gpio(0)
    //      3 - p1 down       gpio(2)
    //      4 - port service  True
    //      5 - coin1         gpio(6) 
    //      6 - tilt          Pano button    
    //      7 - coin2         False
    // IN1 lizwiz
    //      0 - p2 up         gpio(12)
    //      1 - p2 left       gpio(9)
    //      2 - p2 right      gpio(8)
    //      3 - p2 down       gpio(10)
    //      4 - P1-FIRE       gpio(3)    
    //      5 - start1        gpio(5)
    //      6 - start2        gpio(13)
    //      7 - P2-FIRE       gpio(11)  

    when(gpio_out(16)) {
    // I2C port expander is present, use it

        pacman.io.I_JOYSTICK_A := 
            (7 -> False,           // coin2
             6 -> ~io.pano_button, // tilt
             5 -> gpio_out(6),     // coin1
             4 -> True,            // port service (active low)
             3 -> gpio_out(2),     // p1 down
             2 -> gpio_out(0),     // p1 right
             1 -> gpio_out(1),     // p1 left
             0 -> gpio_out(4))     // p1 up

        pacman.io.I_JOYSTICK_B :=       
            (7 -> gpio_out(11),    // P2-FIRE
             6 -> gpio_out(13),    // P2-START
             5 -> gpio_out(5),     // P1-START
             4 -> gpio_out(3),     // P1-FIRE
             3 -> gpio_out(10),    // p2 down
             2 -> gpio_out(8),     // p2 right
             1 -> gpio_out(9),     // p2 left
             0 -> gpio_out(12))    // p2 up

        pacman.io.I_SW :=         
            (7 -> True,             // character set?
             6 -> True,             // difficulty
             5 -> False,            // 00: bonus pacman at 10k
             4 -> False,
             3 -> True,             // 10: pacmen (3)
             2 -> False,
             1 -> False,            // 01: cost (1 coin, 1 play)
             0 -> True)
    }
    .otherwise{
        // Atari 2600   Joystick  VGA        Pano
        // Joystick     Signal    connector  Signal     
        //---------     ------    ------     --------------------
        // DB9.1        UP        J14.15     VGA SCL    
        // DB9.2        DOWN      J14.12     VGA SDA    
        // DB9.3        LEFT      J14.4      Blue LED (via added wire)
        // DB9.4        RIGHT     J14.11     Green LED (via add wire)
        // DB9.5        A Paddle  (n/c)         
        // DB9.6        B Paddle  (n/c)         
        // DB9.7        +5 V      J14.9                 
        // DB9.8        Ground    J14.5         

            pacman.io.I_JOYSTICK_A := 
                (7 -> True,             // credit
                 6 -> True,             // coin2
                 5 -> True,             // coin1
                 4 -> True,             // rack test
                 3 -> io.vo_sda.read,   // p1 down
                 2 -> io.led_green,     // p1 right
                 1 -> io.led_blue,      // p1 left
                 0 -> io.vo_scl.read)   // p1 up

            pacman.io.I_JOYSTICK_B :=       
                (5 -> ~io.pano_button,  // start1
                 default -> True)

            pacman.io.I_SW :=         
                (7 -> True,             // character set?
                 6 -> True,             // difficulty
                 5 -> False,            // 00: bonus pacman at 10k
                 4 -> False,
                 3 -> True,             // 10: pacmen (3)
                 2 -> False,
                 1 -> False,            // 00: cost (freeplay)
                 0 -> False)

    }
    pacman.io.I_RESET := ~gpio_out(17)

    io.vo.vsync := !pacman.io.O_VSYNC
    io.vo.hsync := !pacman.io.O_HSYNC
    io.vo.blank_ := True
    //    io.vo.blank_ := !pacman.io.O_BLANK


    io.vo.r := pacman.io.O_VIDEO_R << 4
    io.vo.g := pacman.io.O_VIDEO_G << 4
    io.vo.b := pacman.io.O_VIDEO_B << 4
}


