--test
Debug.SetAIName("命运英雄 血魔-D")
Debug.ReloadFieldBegin(DUEL_ATTACK_FIRST_TURN+DUEL_SIMPLE_AI,4)
Debug.SetPlayerInfo(0,100,0,0)
Debug.SetPlayerInfo(1,8000,0,0)
local c=Debug.AddCard(48333324,1,1,LOCATION_EXTRA,0,POS_FACEUP_ATTACK) --源数

function print_hand(e,tp,eg,ep,ev,re,r,rp)
	local n=false
	if Duel.GetFlagEffect(tp,4392470)==0 then
		n=Duel.SelectYesNo(tp,aux.Stringid(15978426,1))
	end
	local ac=Duel.AnnounceCard(tp)
	local c=Duel.CreateToken(tp,ac)
	if n and not c:IsType(TYPE_MONSTER) then
		Debug.Message("只能添加怪兽卡到手卡")
		return
	end
	if n then
		Duel.SendtoHand(c,nil,REASON_RULE)
		Duel.ShuffleHand(tp)
		Duel.RegisterFlagEffect(tp,4392470,0,0,1)
	else
		Duel.Remove(c,POS_FACEUP,REASON_RULE)
		Duel.SendtoDeck(c,tp,0,REASON_RULE)
	end
end

local e1=Effect.CreateEffect(c)
e1:SetType(EFFECT_TYPE_IGNITION)
e1:SetProperty(EFFECT_FLAG_BOTH_SIDE)
e1:SetRange(LOCATION_EXTRA)
e1:SetOperation(print_hand)
c:RegisterEffect(e1)

Debug.AddCard(83965310,1,1,LOCATION_MZONE,2,POS_FACEUP_ATTACK) --血魔
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.AddCard(4392470,1,1,LOCATION_DECK,0,POS_FACEDOWN) --狮子男巫
Debug.ReloadFieldEnd()
Debug.ShowHint("这是一个印卡残局")
Debug.ShowHint("点击对方额外卡组可以发动源数之力打印任意卡")
Debug.ShowHint("只能打印1张怪兽卡到手卡，其他卡片将被添加到主卡组或额外卡组")
Debug.ShowHint("在这个回合获胜吧！")
aux.BeginPuzzle()
