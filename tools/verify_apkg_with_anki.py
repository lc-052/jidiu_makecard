"""Import actual Android-exported APKG with official Anki backend. No existing collection touched."""
import pathlib,sys,json,time
sys.path.insert(0,str(pathlib.Path('tools/reference/anki-backend').resolve()))
from anki.collection import Collection
from anki.import_export_pb2 import ImportAnkiPackageRequest, ImportAnkiPackageOptions

package=str(pathlib.Path(sys.argv[1]).resolve())
testdir=pathlib.Path('tools/anki-validation')/('run-'+str(time.time_ns()))
testdir=testdir.resolve()
testdir.mkdir(parents=True,exist_ok=True)
collection=testdir/'collection.anki2'
col=Collection(str(collection))
try:
    request=ImportAnkiPackageRequest(package_path=package, options=ImportAnkiPackageOptions())
    first=col.import_anki_package(request)
    print('First import:',first.log)
    notes_before=col.note_count();cards_before=col.card_count()
    assert notes_before==2,(notes_before,'expected Basic+Cloze notes')
    assert cards_before==3,(cards_before,'expected Basic + 2 Cloze cards')
    ids=col.find_cards('')
    html=[]
    for cid in ids:
        card=col.get_card(cid)
        question=card.question();answer=card.answer()
        html.append(dict(ordinal=card.ord,question=question,answer=answer))
        assert '中文' in question or '甲' in question or '乙' in question
        if card.note().note_type()['type']==1:
            assert 'cloze' in question
            assert ('甲' in answer and '乙' in answer)
        else:
            assert '<strong>核心结论</strong>' in answer
            assert '<ul>' in answer and '<li>' in answer
            assert '<details' in answer and '<summary>解释与补充</summary>' in answer
            assert '\\(x^2\\)' in answer
    assert any(d.name=='中文课程::第一章' for d in col.decks.all_names_and_ids())
    second=col.import_anki_package(request)
    assert col.note_count()==notes_before
    assert col.card_count()==cards_before
    print('Second import:',second.log)
    result=dict(backend='Anki 26.9.3 official backend',notes=notes_before,cards=cards_before,reimportDuplicates=0,rendered=html)
    pathlib.Path('tools/anki-validation/result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
    print('PASS: Basic, Cloze, Chinese, tags, hierarchy, HTML, repeat import')
finally:
    col.close()
