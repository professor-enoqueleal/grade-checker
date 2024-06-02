window.onload = function () {

    var today = new Date();

    var dd = String(today.getDate()).padStart(2, '0');
    var mm = String(today.getMonth() + 1).padStart(2, '0'); //January is 0!
    var yyyy = today.getFullYear();
    var hh = String(today.getHours()).padStart(2, '0');
    var min = String(today.getMinutes()).padStart(2, '0');

    today = yyyy + '-' + mm + '-' + dd + 'T' + hh + ':' + min;

    document.getElementById("since").max = today;
    document.getElementById("until").max = today;
};

function dateFormation(date) {

    var isoDate = new Date(date);

    var offset = -3;
    var localDate = new Date(isoDate.getTime() + (offset * 60 * 60 * 1000));

    var day = String(localDate.getUTCDate()).padStart(2, '0');
    var month = String(localDate.getUTCMonth() + 1).padStart(2, '0');
    var year = localDate.getUTCFullYear();
    var hours = String(localDate.getUTCHours()).padStart(2, '0');
    var minutes = String(localDate.getUTCMinutes()).padStart(2, '0');

    var formattedDate = day + '/' + month + '/' + year + ' ' + hours + ':' + minutes;

    return formattedDate;

}
