#!/bin/sh

#set -x

rom_path_src=../roms
rom_path=.
rom_md5_path=../roms.md5
romgen_path=../../../misc/romgen

build_them() {
   echo "Supported Pacman arcade ROMS verified (${romset} version)"
   rm ${rom_path_src}/main.bin 2> /dev/null
   for file in $main ; do
      cat ${rom_path_src}/${file} >> ${rom_path_src}/main.bin
   done

   rm ${rom_path_src}/rom1.bin 2> /dev/null
   for file in $rom1 ; do
      cat ${rom_path_src}/${file} >> ${rom_path_src}/wiz.bin
   done

   rm ${rom_path_src}/gfx1.bin 2> /dev/null
   for file in $gfx1 ; do
      cat ${rom_path_src}/${file} >> ${rom_path_src}/gfx1.bin
   done

   # generate RTL code for small PROMS
   ${romgen_path}/romgen ${rom_path_src}/$prom1 PROM1_DST 9 l r e > ${rom_path}/prom1_dst.vhd 2> /dev/null
   ${romgen_path}/romgen ${rom_path_src}/$prom4 PROM4_DST 8 l r e > ${rom_path}/prom4_dst.vhd 2> /dev/null
   ${romgen_path}/romgen ${rom_path_src}/$prom7 PROM7_DST 4 l r e > ${rom_path}/prom7_dst.vhd 2> /dev/null

   # generate RAMB structures for larger ROMS
   ${romgen_path}/romgen ${rom_path_src}/gfx1.bin GFX1      13 l r e > ${rom_path}/gfx1.vhd 2> /dev/null
   ${romgen_path}/romgen ${rom_path_src}/main.bin ROM_PGM_0 14 l r e > ${rom_path}/rom0.vhd 2> /dev/null
   ${romgen_path}/romgen ${rom_path_src}/wiz.bin ROM_PGM_1 13 l r e > ${rom_path}/rom1.vhd 2> /dev/null

   echo "Done"
}

if [ ! -e ${romgen_path}/romgen ]; then
   (cd ${romgen_path};make romgen)
fi

if [ ! -e ${romgen_path}/romgen ]; then
   echo "failed to build romgen utility"
   exit 1
fi

(cd ${rom_path_src};md5sum -c ${rom_md5_path}/lizwiz.md5) 2>/dev/null 1> /dev/null
if [ $? -eq 0 ]; then
   romset="lizwiz"
   main="6e.cpu 6f.cpu 6h.cpu 6j.cpu"
   rom1="wiza wizb"
   gfx1="5e.cpu 5f.cpu"
   prom1="82s126.1m"
   prom4="4a.cpu"
   prom7="7f.cpu"
   build_them
   exit
fi

echo "No supported arcade ROMS found"

