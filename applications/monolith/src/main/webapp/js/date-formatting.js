// Limits the Custom period pickers (Since/Until) to the end of today.
// Dates shown on the pages are formatted on the server in the configured time zone
// (milestones.properties -> milestone.timezone), so no fixed offset is applied here.
window.addEventListener('DOMContentLoaded', function () {

    var today = new Date();

    var yyyy = today.getFullYear();
    var mm = String(today.getMonth() + 1).padStart(2, '0'); // January is 0
    var dd = String(today.getDate()).padStart(2, '0');

    var endOfToday = yyyy + '-' + mm + '-' + dd + 'T23:59';

    ['since', 'until'].forEach(function (id) {
        var input = document.getElementById(id);
        if (input) {
            input.max = endOfToday;
        }
    });
});
