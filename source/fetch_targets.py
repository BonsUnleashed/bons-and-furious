"""Download the pinned compile-only targets from their publishers; never install or redistribute them."""
from pathlib import Path
import hashlib,json,urllib.request,zipfile,io

root=Path(__file__).resolve().parent
targets=root.parent/'targets';targets.mkdir(exist_ok=True)
nested=root.parent/'targets-nested';nested.mkdir(exist_ok=True)
def extract(data,prefix):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in z.namelist():
            if name.endswith('.jar'):
                child=z.read(name);sha=hashlib.sha1(child).hexdigest()
                dest=nested/(sha+'-'+Path(name).name)
                if not dest.exists():dest.write_bytes(child)
                extract(child,prefix+'!'+name)
for item in json.loads((root/'targets.lock.json').read_text()):
    dest=targets/item['filename']
    if dest.exists():data=dest.read_bytes()
    else:
        req=urllib.request.Request(item['url'],headers={'User-Agent':'BonsAndFurious/1.0.27 Minecraft-1.21.1 source build'})
        with urllib.request.urlopen(req,timeout=180) as response:data=response.read()
    assert hashlib.sha1(data).hexdigest()==item['sha1'],f'Publisher SHA-1 mismatch: {dest.name}'
    if not dest.exists():dest.write_bytes(data)
    extract(data,dest.name)
    print('Verified',dest.name)
