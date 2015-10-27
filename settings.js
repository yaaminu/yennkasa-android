var fs = require('fs')

var categoryKeys = ['Notifications', 'Storage', 'Network']

var categories = [];

for (var i = 0; i < 3; i++) {
    var category = {}
    category.key = categoryKeys[i]
    category.standAlone = false
    category.type = 1
    category.order = (i + 1) * 10
    categories.push(category)
}

for (var i = categories.length - 1; i >= 0; i--) {
    switch (i) {
        case 0: //notifications
            addNotificationSettings()
            break;
        case 1:
            addStorageSettings()
            break;
        case 2:
            addNetworkSettings();
            break;
        default:
            break;
    }
}


fs.writeFile('./settings.json', new Buffer(JSON.stringify(categories)), function(err, bytesWriten, res) {
    console.log(err || bytesWriten)
})

function addNotificationSettings() {
    var keys = ['inAppNotifications', 'newMessageTone', 'vibrateOnNewMessage', 'litLightOnNewMessage']
    types = [2, 4, 2, 2]
    defaultValues = [true,'default',false,true]
    doAddSettings(1,keys, types,defaultValues)
}

function doAddSettings(index,keys, types,defaultValues) {
	console.log(defaultValues)
	if(index < 1) throw new Erro('invalid order')
	var order = 10*(index)+1
    for (var i = 0; i < keys.length; i++) {
        var subSetting = {}
        subSetting.key = keys[i]
        subSetting.order = order
        subSetting.standAlone = false
        subSetting.type = types[i]
        subSetting.defaultValue = defaultValues[i]
        categories.push(subSetting)
        order++
    }
}


function addStorageSettings() {
    var keys = ['deleteAttachementsOnMessageDelete', 'deleteOldMessages']
    types = [2, 8]
    defaultValues = [false,false]
    doAddSettings(2,keys, types,defaultValues)
}

function addNetworkSettings() {
    var keys = ['autoDownloadMessage']
    types = [4]
    defaultValues = [false]
    doAddSettings(3,keys, types,defaultValues)
}

console.log(JSON.stringify(categories))
