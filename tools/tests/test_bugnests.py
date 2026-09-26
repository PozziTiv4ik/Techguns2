import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_bugnests import bugnest_definition, generate_bugnest_content, bugnest_translations
from legacy_npcs import LEGACY, RESOURCES
from legacy_models import strip_comments

ROOT=Path(__file__).resolve().parents[2]


class BugNestPortTests(unittest.TestCase):
    def test_original_palette_spawner_and_height_contract(self):
        d=bugnest_definition()
        self.assertEqual(d['sphere_effective_tickets'],[17,1,2]); self.assertEqual(d['tunnel_effective_tickets'],[13,1])
        self.assertEqual(d['spawner'],{'mobsLeft':7,'maxActive':3,'spawnDelay':150,'delay':200,'spawnRange':1,'mobtypes':[{'id':'techguns:alienbug','weight':1}]})
        self.assertEqual((d['width_depth_range'],d['height_argument'],d['surface_offset']),([16,31],0,-2))

    def test_original_texture_and_translations(self):
        files=generate_bugnest_content()
        for atlas in ('block','item'):
            self.assertEqual(files[RESOURCES+f'assets/techguns/textures/{atlas}/bugnest_sand.png'],(LEGACY/'resources/assets/techguns/textures/blocks/bugnest_sand.png').read_bytes())
        self.assertEqual(bugnest_translations('en_us')['block.techguns.bugnest_sand'],'Hardened Sand')
        self.assertIn('Песок',bugnest_translations('ru_ru')['block.techguns.bugnest_sand'])

    def test_shared_medium_placement_and_block_resources(self):
        files=generate_bugnest_content()
        placement=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/alienbug_nest.json'])['placement']
        self.assertEqual((placement['spacing'],placement['separation'],placement['salt']),(32,31,1337262))
        self.assertEqual(json.loads(files[RESOURCES+'data/minecraft/tags/block/mineable/shovel.json'])['values'],['techguns:bugnest_sand'])
        self.assertTrue(bugnest_definition()['generation']['ore_toggle_independent'])

    def test_execute_original_geometry_against_port_for_64_plans(self):
        # Compile the original algorithm unchanged against a small block-write world.
        # This oracle checks the source geometry itself, not another reimplementation.
        javac=shutil.which('javac')
        if not javac:
            homes=[Path(os.environ['JAVA_HOME'])] if os.environ.get('JAVA_HOME') else list((ROOT/'.tools/jdk25').glob('*'))
            javac=next((str(p/'bin/javac.exe') for p in homes if (p/'bin/javac.exe').exists()),None)
        self.assertIsNotNone(javac,'JDK 25 required to compare original geometry')
        java=str(Path(javac).with_name('java.exe' if os.name=='nt' else 'java'))
        original=strip_comments((LEGACY/'java/techguns/world/structures/AlienBugNest.java').read_text(encoding='utf-8'))
        original=re.sub(r'^(package|import) .*?;\s*','',original,flags=re.M).replace('public class AlienBugNest','class AlienBugNest').replace('Vec2 xz','MathUtil.Vec2 xz')
        utils=strip_comments((LEGACY/'java/techguns/util/BlockUtils.java').read_text(encoding='utf-8'))
        methods=[]
        for match in re.finditer(r'public static void (fillSphere2|fillSphere|fillCylinder|drawLine)\(',utils):
            start=utils.index('{',match.start()); end=start+1; depth=1
            while depth:
                depth+=(utils[end]=='{')-(utils[end]=='}'); end+=1
            methods.append(utils[match.start():end])
        self.assertEqual(len(methods),5)
        stub=(Path(__file__).parent/'java/BugNestOracle.java').read_text(encoding='utf-8')
        with tempfile.TemporaryDirectory(prefix='techguns-bugnest-') as temp:
            path=Path(temp)/'BugNestOracle.java'; path.write_text(stub+'\n'+original+'\nclass BlockUtils {\n'+'\n'.join(methods)+'\n}',encoding='utf-8')
            compiled=subprocess.run([javac,'-encoding','UTF-8','-d',temp,str(ROOT/'core/src/main/java/techguns/core/BugNestLayout.java'),str(path)],capture_output=True,text=True)
            self.assertEqual(compiled.returncode,0,compiled.stderr)
            checked=subprocess.run([java,'-cp',temp,'BugNestOracle'],capture_output=True,text=True)
            self.assertEqual(checked.returncode,0,checked.stderr)
            self.assertIn('passed for 64 layouts',checked.stdout)


if __name__=='__main__': unittest.main()
