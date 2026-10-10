"""Real official Anki backend import/render/reimport of Android-exported six-template packages."""
import sys,pathlib,time,json
sys.path.insert(0,str(pathlib.Path('tools/reference/anki-backend').resolve()))
from anki.collection import Collection
from anki.import_export_pb2 import ImportAnkiPackageRequest,ImportAnkiPackageOptions
root=pathlib.Path('tools/anki-validation')/('templates-'+str(time.time_ns()))
root.mkdir(parents=True,exist_ok=True)
col=Collection(str((root/'collection.anki2').resolve()))
def request(path):return ImportAnkiPackageRequest(package_path=str(pathlib.Path(path).resolve()),options=ImportAnkiPackageOptions())
try:
    col.import_anki_package(request(sys.argv[1]))
    assert col.note_count()==12 and col.card_count()==18
    mids=set();classes=set();before={};rendered=[]
    for nid in col.find_notes(''):
        n=col.get_note(nid);mids.add(n.mid);before[n.guid]=nid
        for style in ['classic','neon','sweet','forest','paper','minimal']:
            if 'jt-'+style in n.fields[0]:classes.add(style)
    assert len(mids)==2 and len(classes)==6
    for cid in col.find_cards(''):
        card=col.get_card(cid);q=card.question();a=card.answer()
        assert 'jt-' in q and 'jt-' in a
        assert '<details' in a
        if card.note().note_type()['type']==1:
            assert '甲' in a and '乙' in a
        else:
            assert '<strong>核心结论</strong>' in a and '<ul>' in a
        rendered.append(dict(id=cid,ordinal=card.ord,question=q,answer=a))
    col.import_anki_package(request(sys.argv[1]))
    assert col.note_count()==12 and col.card_count()==18
    col.import_anki_package(request(sys.argv[2]))
    assert col.note_count()==12 and col.card_count()==18
    after={col.get_note(nid).guid:nid for nid in col.find_notes('')}
    assert before==after
    n=col.get_note(next(nid for guid,nid in after.items() if guid=='visualCLASSICbasic'))
    assert 'jt-forest' in n.fields[0],n.fields
    result=dict(backend='Anki 26.9.3',notes=12,cards=18,models=2,templates=sorted(classes),reimportDuplicates=0,templateSwitchStableIds=True,rendered=rendered)
    pathlib.Path('tools/anki-validation/templates-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
    print('PASS: six template classes, Basic/Cloze, Chinese, rendering, stable IDs, template switch and duplicate-free reimport')
finally:col.close()
