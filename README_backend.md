上传新卡组调用api：
首先获得卡组id：
【get] http://rarnu.xyz:38383/api/mdpro3/deck/deckId
将获得的卡组id作为json body的一部分，上传到服务器：
http://rarnu.xyz:38383/api/mdpro3/sync/single
json body格式如下：
{
"deckContributor": "zhb_con",
"userId": 795610,
"deck": {
"deckId": "0007b1ad19",
"deckName": "zhb_test_deck2",
"deckCoverCard1": 0,
"deckCoverCard2": 0,
"deckCoverCard3": 0,
"deckCase": 0,
"deckProtector": 0,
"deckYdk": "#created by
mdpro3\r\n#main\r\n89943723\r\n89943724\r\n89943724\r\n79856792\r\n58153103\r\n83965310\r\n14124483\r\n13256226\r\n13256226\r\n9411399\r\n9411399\r\n18094166\r\n18094166\r\n40044918\r\n40044918\r\n59392529\r\n63060238\r\n50720316\r\n50720316\r\n27780618\r\n27780618\r\n16605586\r\n16605586\r\n43237273\r\n80344569\r\n22865492\r\n22865492\r\n17955766\r\n14558127\r\n14558127\r\n14558127\r\n23434538\r\n23434538\r\n23434538\r\n40740224\r\n6186304\r\n18144506\r\n213326\r\n213326\r\n8949584\r\n8949584\r\n10186633\r\n32807848\r\n21143940\r\n21143940\r\n80170678\r\n52947044\r\n45906428\r\n45906428\r\n24094653\r\n24094653\r\n14088859\r\n14088859\r\n24224830\r\n24224830\r\n65681983\r\n24299458\r\n75047173\r\n10045474\r\n75047173\r\n#extra\r\n90050480\r\n86346643\r\n31817415\r\n32828466\r\n40080312\r\n56733747\r\n29095552\r\n40854197\r\n46759931\r\n60461804\r\n93347961\r\n64655485\r\n22908820\r\n58481572\r\n58004362\r\n!
side\r\n27204311\r\n27204311\r\n83965311\r\n59438930\r\n59438930\r\n24508238\r\n24508238\r\n94145021\r\n94145021\r\n26964762\r\n90846359\r\n83326048\r\n83326048\r\n83326048\r\n#pickup\r\n#case\r\n1081008#\r\n#protector\r\n1070008#\r\n#field\r\n1090009#\r\n#grave\r\n1100009#\r\n#stand\r\n1110001#\r\n#mate\r\n1000007#\r\n##\r\n###",
"isDelete": false
}
}
注意：deckId字段为[get]方法获得的id，deckName字段对应于卡组名称，服务端会检查该字段，不允许同一卡组名称上传多次（如果第二次上传某个卡组名，会返回false，上传失败）
上传成功后，服务器响应为：
{
"code": 0,
"message": "",
"data": true
}
上传失败后，服务器响应为：

更新已在云上存在的卡组：
仍然用上述body，但是其中deckId、deckName必须与云上的记录严格对应。
示例：
[1]云上存在卡组deckId=111，deckName=双打水产。
上传json中，deckId=111，deckName=双打水产，代表更新云上的该卡组的内容。
[2]云上存在卡组deckId=111，deckName=双打水产，
上传json中，deckId=110，deckName=双打水产，服务端会返回错误，本次上传失败。
即，如果服务器上已存在卡组名称XXX，则再上传“卡组名为XXX，卡组id不同”的卡组，会返回错误
[3]云上存在卡组deckId=111，deckName=双打水产，
上传json中，deckId=111，deckName=双打水产B，代表更新卡组名称，本次上传成功。
总结：
将对应于deckId、deckName的卡组内容json推送到服务器。
如果在服务器上不存在deckId、deckName对应的记录，则创建新卡组
如果在服务器存在deckId相同的记录，则更新卡组，deckName会覆盖服务器上的卡组名
如果在服务器存在deckName相同、deckId不同的记录，则更新失败