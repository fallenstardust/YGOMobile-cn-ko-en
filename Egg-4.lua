--[[message
更新：2023-03-26
卡数：17
规则：新大师
]]
--created by puzzle editor
Debug.SetAIName("蛋总")
Debug.ReloadFieldBegin(DUEL_ATTACK_FIRST_TURN+DUEL_SIMPLE_AI,4)
Debug.SetPlayerInfo(0,100,0,0)
Debug.SetPlayerInfo(1,16150,0,0)

--自己的怪兽区
Debug.AddCard(14878871,0,0,LOCATION_MZONE,2,POS_FACEUP_ATTACK) --救援猫
--对方的怪兽区
Debug.AddCard(23995346,1,1,LOCATION_MZONE,1,POS_FACEUP_ATTACK) --究极龙骑士
Debug.AddCard(23995346,1,1,LOCATION_MZONE,2,POS_FACEUP_ATTACK) --究极龙骑士
Debug.AddCard(23995346,1,1,LOCATION_MZONE,3,POS_FACEUP_ATTACK) --究极龙骑士
--自己的魔陷区
--对方的魔陷区
Debug.AddCard(77859858,1,1,LOCATION_SZONE,1,POS_FACEDOWN_ATTACK) --步向破灭的速攻抽卡
--自己的手卡
--对方的手卡
--自己的墓地
Debug.AddCard(96930127,0,0,LOCATION_GRAVE,0,POS_FACEUP_ATTACK) --链犬
Debug.AddCard(96930127,0,0,LOCATION_GRAVE,0,POS_FACEUP_ATTACK) --链犬
Debug.AddCard(96930127,0,0,LOCATION_GRAVE,0,POS_FACEUP_ATTACK) --链犬
--对方的墓地
--自己除外的卡
--对方除外的卡
--自己的卡组
Debug.AddCard(49374988,0,0,LOCATION_DECK,0,POS_FACEUP_ATTACK) --魔偶甜点·枫糖浆绵羊
Debug.AddCard(34680482,0,0,LOCATION_DECK,0,POS_FACEUP_ATTACK) --魔偶甜点·果冻天使
Debug.AddCard(91350799,0,0,LOCATION_DECK,0,POS_FACEUP_ATTACK) --魔偶甜点·热香饼猫头鹰

--自己的额外
Debug.AddCard(95169481,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --恐牙狼 钻石恐狼
Debug.AddCard(95169481,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --恐牙狼 钻石恐狼
Debug.AddCard(80764541,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --No.44 白天马
Debug.AddCard(82633039,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --鸟铳士 卡斯泰尔
Debug.AddCard(80796456,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --No.70 大罪蛛
Debug.AddCard(16195942,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --暗叛逆超量龙
Debug.AddCard(67598234,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --电影之骑士 盖亚剑士
Debug.AddCard(3987233,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --金毛妇
Debug.AddCard(3987233,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --金毛妇
Debug.AddCard(79016563,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --二进制女巫

Debug.ReloadFieldEnd()
aux.BeginPuzzle()



local e1=Effect.GlobalEffect()
e1:SetType(EFFECT_TYPE_FIELD+EFFECT_TYPE_CONTINUOUS)
e1:SetCode(EVENT_PHASE_START+PHASE_END)
e1:SetCondition(function(e,tp,eg,ep,ev,re,r,rp) return Duel.GetTurnPlayer()==0 end)
e1:SetOperation(function(e,tp,eg,ep,ev,re,r,rp)
Debug.ShowHint("提示：金毛妇") end)
Duel.RegisterEffect(e1,0)
