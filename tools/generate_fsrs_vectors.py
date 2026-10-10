"""Reproduce fixtures using fsrs==6.3.0 (MIT), with fuzz disabled."""
import sys, json, pathlib, random
from datetime import datetime, timezone, timedelta
sys.path.insert(0, str(pathlib.Path(__file__).parent / 'reference' / 'pylibs'))
from fsrs import Scheduler, Card, Rating, State

def snapshot(card):
    return dict(id='c', noteId='n', state=card.state.name.upper(), step=card.step or 0,
                stability=card.stability or 0, difficulty=card.difficulty or 0,
                lastReviewAt=int(card.last_review.timestamp()*1000) if card.last_review else None,
                dueAt=int(card.due.timestamp()*1000))

clock=datetime(2026,1,1,tzinfo=timezone.utc)
card=Card(due=clock)
random.seed(2026)
cases=[]
for i in range(70):
    clock+=timedelta(seconds=random.choice([60,330,600,86400,120000,172800,604800]))
    for retention in [0.8,0.9,0.97]:
        scheduler=Scheduler(desired_retention=retention, enable_fuzzing=False)
        for rating in Rating:
            next_card, _=scheduler.review_card(card,rating,review_datetime=clock)
            before=snapshot(card)
            if card.last_review is None: before['state']='NEW'
            cases.append(dict(before=before,rating=rating.value,now=int(clock.timestamp()*1000),retention=retention,expected=snapshot(next_card)))
    card,_=Scheduler(enable_fuzzing=False).review_card(card,Rating(random.randint(1,4)),review_datetime=clock)
path=pathlib.Path('app/src/test/resources/fsrs-vectors.json')
path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps(cases),encoding='utf8')
print(f'{len(cases)} official FSRS-6.3.0 reference cases written')
