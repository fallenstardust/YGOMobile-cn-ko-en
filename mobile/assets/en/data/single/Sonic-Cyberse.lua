--[[message
更新：2023-03-27
卡数：17
规则：新大师
]]
Debug.SetAIName("QB20180206音响电子界")
Debug.ReloadFieldBegin(DUEL_ATTACK_FIRST_TURN+DUEL_PSEUDO_SHUFFLE+DUEL_SIMPLE_AI,4)
Debug.SetPlayerInfo(0,100,0,0)
Debug.SetPlayerInfo(1,8500,0,0)

--自己的怪兽区
Debug.AddCard(11801343,0,0,LOCATION_MZONE,1,POS_FACEUP_ATTACK) --垃圾收集员
Debug.AddCard(50366775,0,0,LOCATION_MZONE,2,POS_FACEUP_ATTACK) --格式弹涂鱼
Debug.AddCard(15341821,0,0,LOCATION_MZONE,3,POS_FACEUP_ATTACK) --蒲公英狮
--对方的怪兽区
Debug.AddCard(30532390,1,1,LOCATION_MZONE,0,POS_FACEUP_DEFENSE) --隆隆隆石人
Debug.AddCard(8508055,1,1,LOCATION_MZONE,1,POS_FACEUP_DEFENSE) --时钟共鸣者
Debug.AddCard(65623423,1,1,LOCATION_MZONE,2,POS_FACEUP_DEFENSE) --暗黑共鸣者
--自己的魔陷区
--对方的魔陷区
--自己的手卡
Debug.AddCard(12525049,0,0,LOCATION_HAND,4,POS_FACEUP_ATTACK) --音响战士 吉他手
Debug.AddCard(31826057,0,0,LOCATION_HAND,2,POS_FACEUP_ATTACK) --音响战士 钢琴
Debug.AddCard(68933343,0,0,LOCATION_HAND,3,POS_FACEUP_ATTACK) --音响战士 贝司手
Debug.AddCard(43583400,0,0,LOCATION_HAND,1,POS_FACEUP_ATTACK) --抗锯齿星人
Debug.AddCard(49919798,0,0,LOCATION_HAND,5,POS_FACEUP_ATTACK) --音响战士 合成器
--对方的手卡
--自己的墓地
--对方的墓地
--自己除外的卡
--对方除外的卡
--自己的卡组
Debug.AddCard(94331452,0,0,LOCATION_DECK,0,POS_FACEUP_ATTACK) --音响战士 架子鼓
Debug.AddCard(5399521,0,0,LOCATION_DECK,0,POS_FACEUP_ATTACK) --音响战士 麦克风
Debug.AddCard(34492631,0,0,LOCATION_DECK,0,POS_FACEUP_ATTACK) --废品巨人
Debug.AddCard(7445307,0,0,LOCATION_DECK,0,POS_FACEUP_ATTACK) --双汇编亚龙
Debug.AddCard(15066114,0,0,LOCATION_DECK,0,POS_FACEUP_ATTACK) --分段龙
--对方的卡组
--自己的额外
Debug.AddCard(22423493,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --机壳守护神 路径灵
Debug.AddCard(61665245,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --召唤女巫
Debug.AddCard(70902743,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --红莲魔龙
Debug.AddCard(64880894,0,0,LOCATION_EXTRA,0,POS_FACEDOWN_ATTACK) --星尘充能战士

Debug.ReloadFieldEnd()
aux.BeginPuzzle()



local e1=Effect.GlobalEffect()
e1:SetType(EFFECT_TYPE_FIELD+EFFECT_TYPE_CONTINUOUS)
e1:SetCode(EVENT_PHASE_START+PHASE_END)
e1:SetCondition(function(e,tp,eg,ep,ev,re,r,rp) return Duel.GetTurnPlayer()==0 end)
e1:SetOperation(function(e,tp,eg,ep,ev,re,r,rp)
Debug.ShowHint("提示：蒲公英狮") end)
Duel.RegisterEffect(e1,0)

