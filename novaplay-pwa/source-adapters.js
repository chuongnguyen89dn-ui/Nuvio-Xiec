/* NovaPlay source adapters v0.2. Data reads are direct GitHub Raw requests.
 * No Render endpoint or video proxy. Source pages requiring protected headers
 * cannot be made playable by pretending a page URL is an MP4/HLS stream. */
(function(){
'use strict';
const RAW='https://raw.githubusercontent.com/chuongnguyen89dn-ui/';
const sourceFiles={
  xiec: RAW+'XemXiec/main/ket_qua_1500_phim.json',
  phimhd: RAW+'phimHD/catalog-data/rophim_catalog.json',
  missav: RAW+'missav/main/data/catalog-verified.json',
  ikisoda: RAW+'missav/main/data/ikisoda-catalog.json'
};
const cases=[
 {id:'xiec',name:'XemXiec · MIKR-112',code:'MIKR-112',subtitle:'Lấy #1/#2 từ dataset, KHÔNG chọn trailer.'},
 {id:'phimhd',name:'PhimHD · phim trong catalog',code:'prometheus',subtitle:'Đọc playbackHints từ catalog; player page không phải video.'},
 {id:'missav',name:'MissAV · FTHTD-219',code:'FTHTD-219',subtitle:'HLS 1080p + Referer. Safari có thể bị từ chối HTTP 403.'},
 {id:'ikisoda',name:'IkiSoda · BAZX-390',code:'BAZX-390',subtitle:'URL MP4 ký thời hạn; phải resolve URL mới trên thiết bị.'}
];
const cache=new Map();
const isHttps=u=>{try{return new URL(u).protocol==='https:'}catch{return false}};
const videoUrl=u=>isHttps(u)&&(/\.(m3u8|mp4)(\?|$)/i.test(u)||/remote_control\.php\?/i.test(u));
async function catalog(id){
 if(cache.has(id))return cache.get(id);
 const p=fetch(sourceFiles[id],{cache:'no-store',signal:AbortSignal.timeout(25000)})
 .then(async r=>{if(!r.ok)throw Error('GitHub HTTP '+r.status);return r.json()})
 .catch(e=>{cache.delete(id);throw e});
 cache.set(id,p);return p;
}
function findRow(rows,code){return rows.find(x=>String(x.code||x.title||x.name||'').toUpperCase().includes(code.toUpperCase()))}
function collectVideoURLs(obj,depth=0,out=[]){
 if(depth>5||out.length>40)return out;
 if(typeof obj==='string'){if(videoUrl(obj))out.push(obj);return out}
 if(Array.isArray(obj)){for(const x of obj.slice(0,40))collectVideoURLs(x,depth+1,out);return out}
 if(obj&&typeof obj==='object'){for(const [k,v] of Object.entries(obj)){if(/poster|image|trailer|thumbnail|backdrop/i.test(k))continue;collectVideoURLs(v,depth+1,out)}}return out;
}
function unique(a){return [...new Set(a.filter(videoUrl))]}
function resolveStreamVSMov(u){
 const m=u.match(/^(https:\/\/[^/]*streamvsmov\.com)\/(?:video|embed)\/([0-9a-f-]{16,})(?:[/?#].*)?$/i);
 return m?m[1]+'/stream/'+m[2]+'/master.m3u8':null;
}
function getXiec(m){
 const found=unique([...(m.streams||[]).map(x=>x?.url),m.manifest_url,m.mp4_url]);
 return {links:found.map((url,i)=>({title:(m.streams||[])[i]?.name||'#'+(i+1),url,kind:'direct'})),notes:'Nguồn chính đọc từ dữ liệu tĩnh XemXiec, không dùng manifest Render.'};
}
function getPhimhd(m){
 const urls=unique(collectVideoURLs(m.playbackHints||m.playback_hints||m));
 const src=urls.map((url,i)=>({title:'Nguồn có sẵn '+(i+1),url,kind:'direct'}));
 const candidate=String(m.playbackHints?.url||m.playbackHints?.embed||m.link_embed||'');
 const derived=resolveStreamVSMov(candidate);
 if(derived&&!src.some(s=>s.url===derived))src.push({title:'StreamVSMov HLS (derived)',url:derived,kind:'direct'});
 return {links:src,notes:src.length?'Nguồn lấy từ catalog; link cần được xác minh trên Safari.':'Catalog không chứa direct media. StreamC cần POST bootstrap và phiên truy cập; Safari PWA không thể chạy resolver Python/curl_cffi hiện có.'};
}
function getMissav(m){
 const s=(m.streams_1080||[]).find(x=>x.quality==='1080p'&&x.verification==='master_resolution_1080');
 if(!s)return {links:[],notes:'Chưa có HLS 1080p đã xác minh trong dataset.'};
 const path=new URL(s.url).pathname;
 const mirror='https://surrit.mrstcdn.store'+path;
 return {links:[{title:'Mirror HLS 1080p · thử trực tiếp',url:mirror,kind:'headers',referer:'https://missav.ws/'},{title:'HLS 1080p gốc',url:s.url,kind:'headers',referer:'https://missav.ws/'}],notes:'Nguồn cần Referer/Origin cho cả playlist và segment. Safari web không đặt được các header này. Native player có thể dùng proxy LOCAL trên iPhone.'};
}
function getIkisoda(m){
 const expires=(()=>{try{let q=new URL(m.url).searchParams.get('time');return q?new Date(Number(q)*1000).toLocaleString('vi-VN'):null}catch{return null}})();
 return {links:[],notes:'Bản ghi có MP4 ký token (hạn: '+(expires||'không rõ')+'). Không đưa link cũ vào player. Resolver phải tải source_page hiện tại, phân tích flashvars/get_file và lấy redirect mới; yêu cầu native vì CORS/headers.'};
}
const adapters={xiec:async()=>{let rows=await catalog('xiec');let m=findRow(rows,'MIKR-112');if(!m)throw Error('Không tìm thấy MIKR-112');return getXiec(m)},phimhd:async()=>{let data=await catalog('phimhd');let rows=Array.isArray(data)?data:data.movies||[];let m=findRow(rows,'prometheus')||rows.find(x=>String(x.url||'').includes('prometheus'));if(!m)throw Error('Không tìm thấy bản ghi phim mẫu trong catalog');return getPhimhd(m)},missav:async()=>{let rows=await catalog('missav');let m=findRow(rows,'FTHTD-219');if(!m)throw Error('Không tìm thấy FTHTD-219');return getMissav(m)},ikisoda:async()=>{let data=await catalog('ikisoda');let m=findRow(data.movies||[],'BAZX-390');if(!m)throw Error('Không tìm thấy BAZX-390');return getIkisoda(m)}};
const old=window.renderFixtures;
window.renderFixtures=function(){
 const host=document.getElementById('testcases');if(!host)return;host.replaceChildren();
 for(const item of cases){
 const card=document.createElement('div');card.className='panel';
 const title=document.createElement('h3');title.textContent=item.name;
 const desc=document.createElement('p');desc.className='muted';desc.textContent=item.subtitle;
 const detail=document.createElement('div');detail.className='muted';
 const btn=document.createElement('button');btn.textContent='Lấy nguồn thực tế';
 btn.onclick=async()=>{
   btn.disabled=true;detail.textContent='Đang đọc dữ liệu nguồn...';
   try{
     const result=await adapters[item.id]();detail.replaceChildren();
     const note=document.createElement('p');note.textContent=result.notes;detail.append(note);
     if(!result.links.length){const p=document.createElement('p');p.textContent='Không có URL trực tiếp hợp lệ trong dữ liệu; không giả làm link phát.';detail.append(p)}
     for(const stream of result.links){
       const b=document.createElement('button');b.style.margin='5px';b.textContent='▶ '+stream.title;
       b.onclick=()=>{
         if(stream.kind==='headers' && !confirm('Nguồn yêu cầu Referer. Safari có thể báo 403. Vẫn thử phát trực tiếp?'))return;
         window.tab('player');document.getElementById('movieTitle').textContent=item.name;
         document.getElementById('streams').replaceChildren();
         window.play(stream.url);
       };
       detail.append(b);
     }
   }catch(e){detail.textContent='Không đọc được dữ liệu: '+e.message+' (kiểm tra mạng/CORS hoặc nhánh GitHub).'}
   finally{btn.disabled=false}
 };
 card.append(title,desc,btn,detail);host.append(card);
 }
};
window.renderFixtures();
window.NovaPlayAdapters={getXiec,getPhimhd,getMissav,getIkisoda,resolveStreamVSMov,collectVideoURLs};
})();
