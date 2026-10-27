(function(root){
    'use strict';
    function attrs(text){
        var result={},re=/([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))/g,m;
        while((m=re.exec(text)))result[m[1].toLowerCase()]=m[2]||m[3]||m[4]||'';
        return result;
    }
    function nameAfterComma(line){
        var quote='',i,c;
        for(i=0;i<line.length;i++){c=line.charAt(i);if((c==='"'||c==="'"))quote=quote===c?'':(!quote?c:quote);else if(c===','&&!quote)return line.slice(i+1).trim();}
        return '';
    }
    function splitHeaders(line){
        var i=line.indexOf('|'),url=i<0?line:line.slice(0,i),headers={},pairs,j,p,k,v;
        if(i>=0){pairs=line.slice(i+1).split('&');for(j=0;j<pairs.length;j++){p=pairs[j].indexOf('=');if(p<1)continue;k=decodeURIComponent(pairs[j].slice(0,p));v=decodeURIComponent(pairs[j].slice(p+1));if(k.toLowerCase()==='user-agent')k='User-Agent';if(k.toLowerCase()==='referer'||k.toLowerCase()==='referrer')k='Referer';headers[k]=v;}}
        return {url:url.trim(),headers:headers};
    }
    function parse(content){
        var lines=(content||'').replace(/^\uFEFF/,'').split(/\r\n|\n|\r/),channels=[],seen={},pending=null,headers={},options=[],i,line,a,item,key,duplicate=0,missing=0;
        for(i=0;i<lines.length;i++){
            line=lines[i].trim();if(!line||line.toUpperCase()==='#EXTM3U')continue;
            if(line.toUpperCase().indexOf('#EXTINF:')===0){if(pending)missing++;a=attrs(line);pending={name:nameAfterComma(line)||a['tvg-name']||'Kênh không tên',group:a['group-title']||'Chưa phân nhóm',logo:a['tvg-logo']||'',tvgId:a['tvg-id']||''};headers={};options=[];continue;}
            if(line.toUpperCase().indexOf('#EXTVLCOPT:HTTP-USER-AGENT=')===0){headers['User-Agent']=line.slice(line.indexOf('=')+1);continue;}
            if(line.charAt(0)==='#'){if(pending)options.push(line);continue;}
            item=splitHeaders(line);if(!/^https?:\/\//i.test(item.url)){missing++;pending=null;continue;}
            for(key in item.headers)if(item.headers.hasOwnProperty(key))headers[key]=item.headers[key];
            pending=pending||{name:item.url,group:'Chưa phân nhóm',logo:'',tvgId:''};
            key=item.url+'|'+JSON.stringify(headers)+'|'+options.join('|');
            if(seen[key])duplicate++;else{seen[key]=1;channels.push({name:pending.name,group:pending.group,url:item.url,logo:pending.logo,tvgId:pending.tvgId,headers:headers,options:options});}
            pending=null;headers={};options=[];
        }
        if(pending)missing++;
        return {channels:channels,duplicateCount:duplicate,missingUrlCount:missing};
    }
    root.Nm7M3u={parse:parse};
    if(typeof module!=='undefined'&&module.exports)module.exports=root.Nm7M3u;
}(this));
