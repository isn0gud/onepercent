/**
 * This is the SqlManager to wrtie Data into Database and
 * read the sqlData from the Database.
 */

'use strict';

var mysql = require('mysql'); // MySQL
var moment = require('moment'); //Timestampparser
var config = require('./config.js'); //Configurationfile

var databaseHandler = exports; //exports the Database classes

/* Creates a connectionpool to the Database*/
var connectionPool = mysql.createPool (
  {
    host     : config.sqlDatabaseHost,
    port     : config.sqlDatabasePort,
    user     : config.sqlDatabaseUser,
    password : config.sqlDatabasePassword,
    database : config.sqlDatabase,
    timezone : config.sqlDatabaseTimezone
  }
);

/* Testdata */
var sqlQueryLayout = {
  jobName: 'TopHashtagJob',
  table: 'toptentags',
  params: ['name','timestamp','count']
}
/* Testdata */
var responseData = {
  job: 
    { 
      jobID: '9b6351d695086ce03c799d8504e91240473644f3',
      jobResult: 
        { 
          topHashtags: [ { hashtag: 'harald', anzahl: 200 },
                         { hashtag: 'Brandenburg', anzahl: 100 },
                         { hashtag: 'Neuschwanstein', anzahl: 130 } ],
          countAllHashtags: 500 
        } 
    } 
}

/*
 * Writes the jobResponseData into Database uses the sqlQueryLayout to get the 
 * right Tablename and Tablefields.
 */
databaseHandler.writeDataToDatabase = function(responseJobData) {
  var JobName = 'TopHashtagJob';
  var time = moment();
  var timeStamp = time.format('YYYY-MM-DD HH:mm:ss');

  connectionPool.getConnection(function(err, connection) {

    for (var i=0; i<responseData.job.jobResult.topHashtags.length; i++) {
    
      var sqlInsertQuery = "INSERT INTO "+connection.escape(sqlQueryLayout.table)+" ("
                                            +connection.escape(sqlQueryLayout.params[0])+", "
                                            +connection.escape(sqlQueryLayout.params[1])+","
                                            +connection.escape(sqlQueryLayout.params[2])+") VALUES ("
                                            +connection.escape(responseData.job.jobResult.topHashtags[i].hashtag)+","
                                            +connection.escape(timeStamp)+","
                                            +connection.escape(responseData.job.jobResult.topHashtags[i].anzahl)+");"
      console.log(sqlInsertQuery);
      
      connection.query(sqlInsertQuery, function(err, rows, fields) {
        if(err) throw err;
      });
    }
  connection.release();
  });
}

/* Gets the inserted timestamps from Database by table */
databaseHandler.readTableFromDatabase = function(callback, req) {
  connectionPool.getConnection(function(err, connection) {
    var sqlQuery = "SELECT timestamp FROM " + req.params.table +" GROUP BY timestamp";
    connection.query(sqlQuery, function(err, rows, fields) {
      if (err) throw err;
      callback(rows);
    }, req);
    connection.release();
  });  
}

/* Gets the rows of Data from Database between two dates */
databaseHandler.readTableAndDateFromDatabase = function(callback, req, date, nextDate) {
  connectionPool.getConnection(function(err, connection) {
    var sqlQuery = "SELECT * FROM " + req.params.table +" WHERE timestamp >= '" + date + "' AND timestamp < '" + nextDate + "'";
    connection.query(sqlQuery, function(err, rows, fields) {
      if (err) throw err;
      callback(rows);
    }, req, date, nextDate);
  connection.release();
  });  
}

/* Gets the rows of Data from Database between two dates and order them by countfield */
databaseHandler.readTableAndDateAndHourFromDatabase = function(callback, req, date, nextDate) {
  connectionPool.getConnection(function(err, connection) {
    var sqlQuery = "SELECT * FROM " + req.params.table +" WHERE timestamp >= '" + date + "' AND timestamp < '" + nextDate + "' ORDER BY count DESC";
    connection.query(sqlQuery, function(err, rows, fields) {
      if (err) throw err;
      callback(rows);
    }, req, date, nextDate);
  connection.release();
  });  
}

/* Logs Data*/
function logData(data) {
  console.log('------------------------------------------');
  console.log(data);
  console.log('------------------------------------------');
}
