--[[message
更新：2023-03-26
卡数：12
规则：新大师
简介：不容小觑的似羊动物~~~
]]
--By OURYGO-YGO EZ manager
Debug.SetAIName("顽雨沉风")
Debug.ReloadFieldBegin(DUEL_ATTACK_FIRST_TURN+DUEL_PSEUDO_SHUFFLE+DUEL_SIMPLE_AI,4)
Debug.SetPlayerInfo(0,500,0,0)
Debug.SetPlayerInfo(1,30000,0,0)
local c=Debug.AddCard(55948544,1,1,LOCATION_EXTRA,0,POS_FACEUP_ATTACK) 
local n=0

function print_hand(e,tp,eg,ep,ev,re,r,rp)
  local ac=Duel.AnnounceCard(tp)
  local c=Duel.CreateToken(tp,ac)
  if n<2 then
    Duel.SendtoHand(c,nil,REASON_RULE)
    Duel.ShuffleHand(tp)
    n=n+1
  else
    if n==10 then
      Debug.ShowHint("印卡过多，审判降临！")
      Duel.SetLP(0,0)
    else
      n=n+1
      Duel.Remove(c,POS_FACEUP,REASON_RULE)
      Duel.SendtoDeck(c,tp,0,REASON_RULE)
    end
  end
end

local e1=Effect.CreateEffect(c)
e1:SetType(EFFECT_TYPE_IGNITION)
e1:SetProperty(EFFECT_FLAG_BOTH_SIDE)
e1:SetRange(LOCATION_EXTRA)
e1:SetOperation(print_hand)
c:RegisterEffect(e1)

Debug.AddCard(14558127,1,1,LOCATION_HAND,0,POS_FACEDOWN)
local
m1=Debug.AddCard(65403020,0,0,LOCATION_MZONE,2,POS_FACEUP_ATTACK)
Debug.AddCard(67441435,0,0,LOCATION_GRAVE,0,POS_FACEUP_ATTACK)
Debug.AddCard(81035362,0,0,LOCATION_MZONE,1,POS_FACEUP_ATTACK)
Debug.AddCard(86099788,1,1,LOCATION_MZONE,4,POS_FACEUP_ATTACK)
Debug.AddCard(11366199,1,1,LOCATION_MZONE,3,POS_FACEUP_ATTACK)
Debug.AddCard(41147577,1,1,LOCATION_MZONE,2,POS_FACEUP_ATTACK)
Debug.AddCard(26268488,1,1,LOCATION_MZONE,1,POS_FACEUP_ATTACK)
Debug.AddCard(17016362,1,1,LOCATION_MZONE,0,POS_FACEUP_ATTACK)
local
s1=Debug.AddCard(19508728,0,0,LOCATION_SZONE,2,POS_FACEUP)
Debug.PreEquip(s1,m1)
Debug.AddCard(68462976,1,1,LOCATION_SZONE,5,POS_FACEUP)



Debug.ReloadFieldEnd()
Debug.ShowHint("这是一个印卡残局，点击对方额外卡组可以发动《法老的审判》打印任意卡")
Debug.ShowHint("只能打印2张卡到手卡，其他8张卡片将被添加到主卡组或额外卡组。")
aux.BeginPuzzle()




