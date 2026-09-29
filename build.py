"""Build from source using a local Forge 1.20.1 SRG development classpath.

No dependency JARs are redistributed. JDK 17+ and Python 3.11+ required.
Pass --minecraft-dir, --forge-libraries and --java-home for your local install.
The script writes only build/ and dist/ beneath this checkout.
"""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, time, zipfile

ROOT=Path(__file__).resolve().parent

def main():
 p=argparse.ArgumentParser()
 p.add_argument('--minecraft-dir',type=Path,required=True)
 p.add_argument('--forge-libraries',type=Path,required=True)
 p.add_argument('--java-home',type=Path,required=True)
 a=p.parse_args()
 work=ROOT/'build'/str(time.time_ns());work.mkdir(parents=True)
 nested=work/'nested';nested.mkdir()
 mods=sorted((a.minecraft_dir/'mods').glob('*.jar'))
 mods=[p for p in mods if not p.name.startswith(('bons_pure_optimizations','bons_and_furious','bons_valkyrien_fixes'))]
 vs=[p for p in mods if p.name=='valkyrienskies-120-2.4.11.jar']
 if len(vs)!=1:raise SystemExit('The unmodified Valkyrien Skies 2.4.11 dependency is required.')
 if hashlib.sha256(vs[0].read_bytes()).hexdigest()!='f99f24de62015451a047f90484cf9d25970cac350105b581c22b2d5247ebfd53':raise SystemExit('Unexpected Valkyrien Skies dependency bytes')
 lib=a.minecraft_dir/'libraries'
 srg=sorted((lib/'net/minecraft/client').rglob('*srg.jar'))
 if not srg:raise SystemExit('A Forge 1.20.1 SRG client development JAR is required under libraries/net/minecraft/client.')
 deps=[*srg,*a.forge_libraries.joinpath('net/minecraftforge/forge').rglob('*universal.jar'),*a.forge_libraries.rglob('*.jar'),*lib.rglob('*.jar'),*mods]
 for jar in mods:
  with zipfile.ZipFile(jar) as z:
   for n in z.namelist():
    if n.endswith('.jar'):
     dest=nested/(hashlib.sha256((jar.name+'/'+n).encode()).hexdigest()[:16]+'.jar');dest.write_bytes(z.read(n));deps.append(dest)
 cp=os.pathsep.join(dict.fromkeys(str(p.resolve()) for p in deps))
 def run(label,exe,args):
  argfile=work/(label+'.args')
  argfile.write_text('\n'.join('"'+str(x).replace('\\','/').replace('"','\\"')+'"' for x in args),encoding='utf-8')
  binary=a.java_home/'bin'/(exe+('.exe' if os.name=='nt' else ''))
  subprocess.run([str(binary),'@'+str(argfile)],check=True,cwd=ROOT)
 tools=work/'tools';tools.mkdir()
 run('tools','javac',['--release','17','-proc:none','-cp',cp,'-d',tools,*sorted((ROOT/'tools').glob('*.java'))])
 stub=work/'compiler-view';stub.mkdir()
 run('compiler-view','java',['-cp',str(tools)+os.pathsep+cp,'CompileSupport',vs[0],stub])
 raw=work/'classes';raw.mkdir()
 sources=sorted((ROOT/'src').rglob('*.java'));gate=[s for s in sources if s.name=='ChunkSetGate.java'];assert len(gate)==1
 run('compile','javac',['--release','17','-proc:none','-encoding','UTF-8','-cp',str(stub)+os.pathsep+cp,'-d',raw,*[s for s in sources if s not in gate]])
 relocated=work/'relocated';relocated.mkdir()
 mappings=json.loads((ROOT/'relocations.json').read_text())
 run('relocate','java',['-cp',str(tools)+os.pathsep+cp,'Relocate',raw,relocated,*[s for pair in mappings.items() for s in pair]])
 run('gate','javac',['--release','17','-proc:none','-encoding','UTF-8','-cp',str(relocated)+os.pathsep+cp,'-d',relocated,*gate])
 entries={p.relative_to(relocated).as_posix():p.read_bytes() for p in relocated.rglob('*.class')}
 entries.update({p.relative_to(ROOT/'resources').as_posix():p.read_bytes() for p in (ROOT/'resources').rglob('*') if p.is_file()})
 dest=ROOT/'dist/bons_and_furious-1.0.19.jar';dest.parent.mkdir(exist_ok=True)
 with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
  for n,data in sorted(entries.items()):
   info=zipfile.ZipInfo(n,(2026,9,28,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
 print(dest.name,hashlib.sha256(dest.read_bytes()).hexdigest())
if __name__=='__main__':main()
