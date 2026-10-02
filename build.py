"""Build Bons and Furious from source using a local Forge 1.20.1 SRG development classpath.

JDK 17+ and Python 3.11+ required. Pass --minecraft-dir, --forge-libraries and --java-home for your local install.
The Minecraft directory supplies libraries/ (with the Forge-generated client *-srg.jar) and mods/ (the tested target
mods, see upstream-credits.json). No Minecraft or target mod JAR is redistributed.

Two MIT mixin libraries are bundled as Jar-in-Jar (MixinExtras 0.5.0, MixinSquared 0.3.6-beta.1). The build takes them
from --deps-dir when given, otherwise downloads them from their Maven repositories; either way each file must match the
sha1 below or the build stops.

Steps: compile our sources (mixins, helpers, config), relocate our helper classes out of the target mods' packages,
check every guard fingerprint in patches/*.json against the local target jars and write bons_and_furious.guards.tsv,
then package the jar with a generated manifest listing every mixin config. The script writes only build/ and dist/.
"""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, time, urllib.request, zipfile

ROOT = Path(__file__).resolve().parent
VERSION = '1.0.26'
FORGE = '1.20.1-47.4.16'
DEPS = [
    dict(file='mixinextras-forge-0.5.0.jar', group='io.github.llamalad7', artifact='mixinextras-forge', version='0.5.0',
         range='[0.5.0,)', sha1='0cebe4d98a42a4a32ebf0ddec7b93d5bdbdad009',
         url='https://repo1.maven.org/maven2/io/github/llamalad7/mixinextras-forge/0.5.0/mixinextras-forge-0.5.0.jar'),
    dict(file='mixinsquared-forge-0.3.6-beta.1.jar', group='com.github.bawnorton.mixinsquared', artifact='mixinsquared-forge',
         version='0.3.6-beta.1', range='[0.3.6-beta.1,)', sha1='a8f98c19747ead4727c6fc4734a9f1304d462e7d',
         url='https://maven.bawnorton.com/releases/com/github/bawnorton/mixinsquared/mixinsquared-forge/0.3.6-beta.1/mixinsquared-forge-0.3.6-beta.1.jar'),
]
LEGACY_CONFIGS = ['bons_and_furious_valkyrien.mixins.json', 'bons_and_furious.mixins.json', 'terrain_efficiency.mixins.json',
                  'bons_and_furious_pacing.mixins.json']
EPOCH = (2026, 10, 1, 0, 0, 0)


def sha1(b):
    return hashlib.sha1(b).hexdigest()


def manifest_line(name, value):
    """One manifest attribute wrapped as the JAR specification requires: at most 72 bytes per line, continuation lines
    start with one space. (Java's reader rejects any line over 512 bytes, and the MixinConfigs list is longer.)"""
    data = f'{name}: {value}'.encode('utf-8')
    lines, first = [], True
    while data:
        width = 72 if first else 71
        cut = min(width, len(data))
        while cut < len(data) and (data[cut] & 0xC0) == 0x80:   # never split a UTF-8 sequence
            cut -= 1
        lines.append((b'' if first else b' ') + data[:cut])
        data, first = data[cut:], False
    return b'\r\n'.join(lines) + b'\r\n'


def fetch_deps(deps_dir, work):
    out = []
    for d in DEPS:
        local = deps_dir / d['file'] if deps_dir else None
        if local and local.is_file():
            data = local.read_bytes()
        else:
            with urllib.request.urlopen(d['url'], timeout=120) as r:
                data = r.read()
        if sha1(data) != d['sha1']:
            raise SystemExit(f"{d['file']}: sha1 {sha1(data)} does not match the expected {d['sha1']}")
        p = work / 'deps' / d['file']
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
        out.append((d, p))
    return out


def nested(jar, dest):
    """The library jars nested in a jar (META-INF/jars or META-INF/jarjar), extracted for the compile classpath."""
    found = []
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if n.endswith('.jar') and n.startswith('META-INF/'):
                p = dest / (hashlib.sha256((jar.name + '/' + n).encode()).hexdigest()[:16] + '.jar')
                p.write_bytes(z.read(n))
                found.append(p)
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--minecraft-dir', type=Path, required=True)
    ap.add_argument('--forge-libraries', type=Path, required=True)
    ap.add_argument('--java-home', type=Path, required=True)
    ap.add_argument('--deps-dir', type=Path)
    a = ap.parse_args()
    work = ROOT / 'build' / str(time.time_ns())
    work.mkdir(parents=True)
    nested_dir = work / 'nested'
    nested_dir.mkdir()
    mods = sorted(p for p in (a.minecraft_dir / 'mods').glob('*.jar')
                  if not p.name.startswith(('bons_pure_optimizations', 'bons_and_furious', 'bons_valkyrien_fixes')))
    vs = [p for p in mods if p.name == 'valkyrienskies-120-2.4.11.jar']
    if len(vs) != 1:
        raise SystemExit('The unmodified Valkyrien Skies 2.4.11 dependency is required.')
    if hashlib.sha256(vs[0].read_bytes()).hexdigest() != 'f99f24de62015451a047f90484cf9d25970cac350105b581c22b2d5247ebfd53':
        raise SystemExit('Unexpected Valkyrien Skies dependency bytes')
    lib = a.minecraft_dir / 'libraries'
    srg = sorted((lib / 'net/minecraft/client').rglob('*srg.jar'))
    if not srg:
        raise SystemExit('A Forge 1.20.1 SRG client development JAR is required under libraries/net/minecraft/client.')
    patched = [p for p in [lib / f'net/minecraftforge/forge/{FORGE}/forge-{FORGE}-client.jar',
                           a.forge_libraries / f'net/minecraftforge/forge/{FORGE}/forge-{FORGE}-server.jar'] if p.is_file()]
    deps = fetch_deps(a.deps_dir, work)
    core = [q for _, p in deps for q in nested(p, nested_dir)]
    mod_nested = [q for m in mods for q in nested(m, nested_dir)]
    forge_jars = sorted(a.forge_libraries.rglob('*.jar'))
    cp_list = [*patched[:1], *srg, *forge_jars, *sorted(lib.rglob('*.jar')), *mods, *mod_nested, *core]
    cp = os.pathsep.join(dict.fromkeys(str(p.resolve()) for p in cp_list))

    def run(label, exe, args):
        argfile = work / (label + '.args')
        argfile.write_text('\n'.join('"' + str(x).replace('\\', '/').replace('"', '\\"') + '"' for x in args), encoding='utf-8')
        binary = a.java_home / 'bin' / (exe + ('.exe' if os.name == 'nt' else ''))
        subprocess.run([str(binary), '@' + str(argfile)], check=True, cwd=ROOT)

    tools = work / 'tools'
    tools.mkdir()
    run('tools', 'javac', ['--release', '17', '-proc:none', '-cp', cp, '-d', tools,
                           ROOT / 'tools/CompileSupport.java', ROOT / 'tools/Relocate.java'])
    stub = work / 'compiler-view'
    stub.mkdir()
    run('compiler-view', 'java', ['-cp', str(tools) + os.pathsep + cp, 'CompileSupport', vs[0], stub])
    raw = work / 'classes'
    raw.mkdir()
    sources = sorted((ROOT / 'src').rglob('*.java'))
    run('compile', 'javac', ['--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', str(stub) + os.pathsep + cp,
                             '-d', raw, *sources])
    relocated = work / 'relocated'
    relocated.mkdir()
    mappings = json.loads((ROOT / 'relocations.json').read_text())
    run('relocate', 'java', ['-cp', str(tools) + os.pathsep + cp, 'Relocate', raw, relocated,
                             *[s for pair in mappings.items() for s in pair]])
    guardtool = work / 'guardtool'
    guardtool.mkdir()
    run('guardtool', 'javac', ['--release', '17', '-proc:none', '-cp', str(relocated) + os.pathsep + cp, '-d', guardtool,
                               ROOT / 'tools/GuardTool.java'])
    guards = work / 'guards' / 'bons_and_furious.guards.tsv'
    # nested jars too: C2ME ships its modules and Radium its config library as Jar-in-Jar (radium_c2me guards);
    # 1.0.23: Forge's own classes (ServerStatusPing, ForgeI18n) come from the universal jar
    universal = a.forge_libraries / f'net/minecraftforge/forge/{FORGE}/forge-{FORGE}-universal.jar'
    if not universal.is_file():
        universal = lib / f'net/minecraftforge/forge/{FORGE}/forge-{FORGE}-universal.jar'
    roots = [*patched[:1], *srg, *([universal] if universal.is_file() else []), *mods, *mod_nested]
    run('guards', 'java', ['-cp', os.pathsep.join([str(guardtool), str(relocated), cp]), 'GuardTool', 'emit',
                           ROOT / 'patches', ROOT / 'resources', guards, *roots])

    entries = {p.relative_to(relocated).as_posix(): p.read_bytes() for p in relocated.rglob('*.class')}
    resources = ROOT / 'resources'
    for p in resources.rglob('*'):
        if p.is_file():
            name = p.relative_to(resources).as_posix()
            if name == 'META-INF/MANIFEST.MF':
                continue
            entries[name] = p.read_bytes()
    entries['bons_and_furious.guards.tsv'] = guards.read_bytes()
    configs = sorted(p.name for p in resources.glob('*.mixins.json'))
    ordered = [c for c in LEGACY_CONFIGS if c in configs] + [c for c in configs if c not in LEGACY_CONFIGS]
    entries['META-INF/MANIFEST.MF'] = (manifest_line('Manifest-Version', '1.0') + manifest_line('Implementation-Version', VERSION)
                                       + manifest_line('FMLModType', 'MOD') + manifest_line('MixinConfigs', ','.join(ordered)) + b'\r\n')
    jarjar = []
    for d, p in deps:
        entries['META-INF/jarjar/' + d['file']] = p.read_bytes()
        jarjar.append({'identifier': {'group': d['group'], 'artifact': d['artifact']},
                       'version': {'range': d['range'], 'artifactVersion': d['version']},
                       'path': 'META-INF/jarjar/' + d['file'], 'isObfuscated': False})
    entries['META-INF/jarjar/metadata.json'] = (json.dumps({'jars': jarjar}, indent=2) + '\n').encode('utf-8')

    dest = ROOT / 'dist' / f'bons_and_furious-{VERSION}.jar'
    dest.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, data in sorted(entries.items()):
            info = zipfile.ZipInfo(n, EPOCH)
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    print(dest.name, hashlib.sha256(dest.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
