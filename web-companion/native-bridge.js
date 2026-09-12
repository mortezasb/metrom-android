'use strict';
(() => {
  const state={connected:false,port:null,hostVersion:null,capabilities:new Set(),activation:null,lastActivationAt:0};

  const readNumber=(el,fallback,min,max)=>{
    const raw=el?.value ?? el?.getAttribute?.('value');
    const n=Number(raw);
    return Number.isFinite(n)?Math.max(min,Math.min(max,Math.round(n))):fallback;
  };
  const readPayload=()=>({
    bpm:readNumber(document.getElementById('bpmInput'),90,35,240),
    timeSignature:String(document.getElementById('timeSignature')?.value||'4/4'),
    masterVolume:readNumber(document.getElementById('masterVolume'),80,0,100),
    startAtEpochMs:Date.now()+80
  });

  const launchForegroundActivation=()=>{
    if(!state.connected||!state.capabilities.has('foreground-activation-intent')||!state.activation)return false;
    const now=Date.now();
    if(now-state.lastActivationAt<500)return false;
    const scheme=String(state.activation.scheme||'');
    const host=String(state.activation.host||'');
    const pkg=String(state.activation.package||'');
    if(!/^[a-z][a-z0-9+.-]*$/i.test(scheme)||!/^[a-z0-9.-]+$/i.test(host)||!/^[a-zA-Z0-9_.]+$/.test(pkg))return false;
    const p=readPayload();
    const qs=new URLSearchParams({bpm:String(p.bpm),sig:p.timeSignature,vol:String(p.masterVolume),startAt:String(p.startAtEpochMs)}).toString();
    const intent=`intent://${host}/start?${qs}#Intent;scheme=${scheme};package=${pkg};end`;
    state.lastActivationAt=now;
    try{window.location.href=intent;return true}catch(_){return false}
  };

  const api={
    get connected(){return state.connected},
    get hostVersion(){return state.hostVersion},
    ownsBackgroundMetronome(){return state.connected&&state.capabilities.has('background-metronome')},
    send(type,payload={}){if(!state.port)return false;try{state.port.postMessage(JSON.stringify({namespace:'metrom',bridge:1,type,payload}));return true}catch(_){return false}},
    playback(action,payload={}){return this.send('playback',{action,...payload})},
    update(payload={}){return this.send('update',payload)},
    requestForegroundPlayback(){return launchForegroundActivation()}
  };
  Object.defineProperty(window,'MetromNativeBridge',{value:api,configurable:false,writable:false});

  window.addEventListener('message',event=>{
    const port=event.ports&&event.ports[0];if(!port)return;
    let hello=null;try{hello=typeof event.data==='string'?JSON.parse(event.data):event.data}catch(_){}
    if(!hello||hello.namespace!=='metrom-native'||Number(hello.bridge)!==1)return;
    if(event.origin&&event.origin.startsWith('http')&&event.origin!==location.origin)return;
    state.port=port;
    state.connected=true;
    state.hostVersion=String(hello.version||'1');
    state.capabilities=new Set(Array.isArray(hello.capabilities)?hello.capabilities:[]);
    state.activation=hello.activation&&typeof hello.activation==='object'?hello.activation:null;
    try{port.start?.()}catch(_){}
    port.onmessage=e=>{
      let msg=null;try{msg=typeof e.data==='string'?JSON.parse(e.data):e.data}catch(_){}
      if(!msg||msg.namespace!=='metrom-native')return;
      window.dispatchEvent(new CustomEvent('metrom-native-message',{detail:msg}));
    };
    api.send('web-ready',{version:String(window.METROM_APP_VERSION||''),url:location.href});
    window.dispatchEvent(new CustomEvent('metrom-native-ready'));
  });

  // On Android 12+, a TWA is visually owned by the browser package. Starting a
  // foreground service only from the host postMessage callback can therefore be
  // rejected as a background start. This hook turns the user's actual Play tap
  // into a tiny explicit native Activity launch. It runs after the app's own click
  // handler, so BPM/meter/volume are already current, then returns immediately.
  document.addEventListener('click',event=>{
    const play=event.target?.closest?.('#playBtn');
    if(!play||play.disabled)return;
    if(play.getAttribute('aria-pressed')==='true')return; // this click is pause
    queueMicrotask(()=>{
      if(play.getAttribute('aria-pressed')!=='true')return;
      const metronome=document.getElementById('metronomeToggle');
      if(metronome && !metronome.checked)return;
      launchForegroundActivation();
    });
  },true);

  // If the user turns the metronome on while transport is already running, start
  // the native service from that same user gesture as well.
  document.addEventListener('change',event=>{
    const target=event.target;
    if(!target||target.id!=='metronomeToggle')return;
    queueMicrotask(()=>{
      if(!target.checked)return;
      const play=document.getElementById('playBtn');
      if(play?.getAttribute('aria-pressed')==='true')launchForegroundActivation();
    });
  },true);
})();
