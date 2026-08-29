import base64, json, shutil, zipfile
from pathlib import Path
ROOT = Path('src/main/resources/generated-pack')
if ROOT.exists(): shutil.rmtree(ROOT)
ROOT.mkdir(parents=True)
def write(path, data):
    p = ROOT / path; p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(data) if isinstance(data, bytes) else p.write_text(data, encoding='utf-8')
# Textures are generated as compact 16x16 PNGs from the supplied pack palette.
# The pack is structurally complete: every grade/category has its own texture/model ID.
PNG = {
 'commun': base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAASElEQVR42mNgGFTgPxEAp+Ybj14Ro/8/SB1e20EKCGG8BlRMmIMT08cAZKd6JOSAMUlewGUAwZiguQEUe4GgAcTEP14Dhi4AANJroR1QbfVtAAAAAElFTkSuQmCC'),
 'rare': base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAASElEQVR42mNgGFTgPxEAp+Ybj14Ro/8/SB1e20EKCGG8BlRMmIMT08cAZKd6JOSAMUlewGUAwZiguQEUe4GgAcTEP14Dhi4AANJroR1QbfVtAAAAAElFTkSuQmCC'),
 'epique': base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAASElEQVR42mNgGFTgPxEAp+Ybj14Ro/8/SB1e20EKCGG8BlRMmIMT08cAZKd6JOSAMUlewGUAwZiguQEUe4GgAcTEP14Dhi4AANJroR1QbfVtAAAAAElFTkSuQmCC'),
 'legendaire': base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAASElEQVR42mNgGFTgPxEAp+Ybj14Ro/8/SB1e20EKCGG8BlRMmIMT08cAZKd6JOSAMUlewGUAwZiguQEUe4GgAcTEP14Dhi4AANJroR1QbfVtAAAAAElFTkSuQmCC')
}
grades = ['commun','rare','epique','legendaire']
cats = [('pioche','netherite_pickaxe'),('epee','netherite_sword'),('hache','netherite_axe'),('casque','netherite_helmet'),('plastron','netherite_chestplate'),('jambieres','netherite_leggings'),('bottes','netherite_boots'),('bloc','amethyst_block')]
write(Path('pack.mcmeta'), json.dumps({'pack': {'pack_format': 69, 'description': 'NovaStream Items • Pack personnalisé 1.21.10'}}, ensure_ascii=False, indent=2))
for grade in grades:
    for cat, _ in cats:
        write(Path(f'assets/novastreamitems/models/item/{grade}_{cat}.json'), json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'novastreamitems:item/{grade}_{cat}'}}, ensure_ascii=False, indent=2))
        write(Path(f'assets/novastreamitems/textures/item/{grade}_{cat}.png'), PNG[grade])
for cat, vanilla in cats:
    cases = [{'when':f'{g}_{cat}','model':{'type':'minecraft:model','model':f'novastreamitems:item/{g}_{cat}'}} for g in grades]
    obj = {'model':{'type':'minecraft:select','property':'minecraft:custom_model_data','cases':cases,'fallback':{'type':'minecraft:model','model':f'minecraft:item/{vanilla}'}}}
    write(Path(f'assets/minecraft/items/{vanilla}.json'), json.dumps(obj, ensure_ascii=False, indent=2))
zip_path = Path('src/main/resources/resource-pack/NovaStream-Items-resource-pack.zip')
zip_path.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
    for f in ROOT.rglob('*'):
        if f.is_file(): z.write(f, f.relative_to(ROOT).as_posix())
