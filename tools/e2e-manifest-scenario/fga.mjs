const STORE='01M33FMPQFJHE3509XJC58BS61';
export async function read(tuple){const r=await fetch(`http://localhost:8080/stores/${STORE}/read`,
 {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({tuple_key:tuple})});
 const j=await r.json();return (j.tuples||[]).map(t=>`${t.key.user} ${t.key.relation} ${t.key.object}`);}
export async function check(user,relation,object){const r=await fetch(`http://localhost:8080/stores/${STORE}/check`,
 {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({tuple_key:{user,relation,object}})});
 const j=await r.json();return j.allowed===true;}
