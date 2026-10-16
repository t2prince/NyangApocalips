# 화면 글자(한국어 원문) 뽑기: python3 scripts/i18n_extract.py app/src/main/java/com/jamcat/nayngapo Plan/i18n_ko_strings.json
# 새로 생긴 원문은 app/src/main/assets/i18n/en.json 에 번역을 채운다 (없으면 한국어로 보인다).
import re,os,sys,json
root=sys.argv[1]
HANG=re.compile('[\uac00-\ud7a3]')
out={}
def strip_comments(src):
    # remove // comments and /* */ while respecting strings (simple state machine)
    res=[];i=0;n=len(src)
    while i<n:
        c=src[i]
        if src.startswith('"""',i):
            j=src.find('"""',i+3); j=n if j<0 else j+3; res.append(src[i:j]); i=j; continue
        if c=='"':
            j=i+1
            while j<n and src[j]!='"':
                if src[j]=='\\': j+=1
                elif src[j]=='$' and j+1<n and src[j+1]=='{':
                    d=1;j+=2
                    while j<n and d>0:
                        if src[j]=='{': d+=1
                        elif src[j]=='}': d-=1
                        j+=1
                    continue
                j+=1
            res.append(src[i:j+1]); i=j+1; continue
        if src.startswith('//',i):
            j=src.find('\n',i); j=n if j<0 else j; i=j; continue
        if src.startswith('/*',i):
            j=src.find('*/',i+2); j=n if j<0 else j+2; i=j; continue
        if c=="'":
            j=i+1
            while j<n and src[j]!="'":
                if src[j]=='\\': j+=1
                j+=1
            res.append(src[i:j+1]); i=j+1; continue
        res.append(c); i+=1
    return ''.join(res)
def literals(src):
    i=0;n=len(src)
    while i<n:
        if src[i]=='"' and not src.startswith('"""',i):
            j=i+1; parts=[]; buf=''
            while j<n and src[j]!='"':
                if src[j]=='\\': buf+=src[j:j+2]; j+=2; continue
                if src[j]=='$' and j+1<n and src[j+1]=='{':
                    d=1;k=j+2
                    while k<n and d>0:
                        if src[k]=='{': d+=1
                        elif src[k]=='}': d-=1
                        k+=1
                    parts.append(buf); buf=''; j=k; continue
                if src[j]=='$' and j+1<n and (src[j+1].isalpha() or src[j+1]=='_'):
                    k=j+1
                    while k<n and (src[k].isalnum() or src[k]=='_'): k+=1
                    parts.append(buf); buf=''; j=k; continue
                buf+=src[j]; j+=1
            parts.append(buf)
            yield parts
            i=j+1
        else: i+=1
for dp,dn,fn in os.walk(root):
    for f in fn:
        if not f.endswith('.kt') or f=='I18n.kt': continue
        src=strip_comments(open(os.path.join(dp,f)).read())
        for parts in literals(src):
            if not any(HANG.search(p) for p in parts): continue
            tpl=''
            for k,p in enumerate(parts):
                p=p.replace('\\n','\n').replace('\\"','"').replace('\\\\','\\')
                tpl+=p
                if k<len(parts)-1: tpl+='{%d}'%k
            out.setdefault(tpl,f)
json.dump(out,open(sys.argv[2],'w'),ensure_ascii=False,indent=0)
print(len(out), sum(1 for t in out if '{' in t))
