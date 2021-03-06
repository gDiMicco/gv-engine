angular.module('gvconsole')
.factory('Base64', function () {
	    /* jshint ignore:start */

	    var keyStr = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';

	    return {
	        encode: function (input) {
	            var output = "";
	            var chr1, chr2, chr3 = "";
	            var enc1, enc2, enc3, enc4 = "";
	            var i = 0;

	            do {
	                chr1 = input.charCodeAt(i++);
	                chr2 = input.charCodeAt(i++);
	                chr3 = input.charCodeAt(i++);

	                enc1 = chr1 >> 2;
	                enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
	                enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
	                enc4 = chr3 & 63;

	                if (isNaN(chr2)) {
	                    enc3 = enc4 = 64;
	                } else if (isNaN(chr3)) {
	                    enc4 = 64;
	                }

	                output = output +
	                    keyStr.charAt(enc1) +
	                    keyStr.charAt(enc2) +
	                    keyStr.charAt(enc3) +
	                    keyStr.charAt(enc4);
	                chr1 = chr2 = chr3 = "";
	                enc1 = enc2 = enc3 = enc4 = "";
	            } while (i < input.length);

	            return output;
	        },

	        decode: function (input) {
	            var output = "";
	            var chr1, chr2, chr3 = "";
	            var enc1, enc2, enc3, enc4 = "";
	            var i = 0;

	            // remove all characters that are not A-Z, a-z, 0-9, +, /, or =
	            var base64test = /[^A-Za-z0-9\+\/\=]/g;
	            if (base64test.exec(input)) {
	                window.alert("There were invalid base64 characters in the input text.\n" +
	                    "Valid base64 characters are A-Z, a-z, 0-9, '+', '/',and '='\n" +
	                    "Expect errors in decoding.");
	            }
	            input = input.replace(/[^A-Za-z0-9\+\/\=]/g, "");

	            do {
	                enc1 = keyStr.indexOf(input.charAt(i++));
	                enc2 = keyStr.indexOf(input.charAt(i++));
	                enc3 = keyStr.indexOf(input.charAt(i++));
	                enc4 = keyStr.indexOf(input.charAt(i++));

	                chr1 = (enc1 << 2) | (enc2 >> 4);
	                chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
	                chr3 = ((enc3 & 3) << 6) | enc4;

	                output = output + String.fromCharCode(chr1);

	                if (enc3 != 64) {
	                    output = output + String.fromCharCode(chr2);
	                }
	                if (enc4 != 64) {
	                    output = output + String.fromCharCode(chr3);
	                }

	                chr1 = chr2 = chr3 = "";
	                enc1 = enc2 = enc3 = enc4 = "";

	            } while (i < input.length);

	            return output;
	        }
	    };

	    /* jshint ignore:end */
});

angular.module('gvconsole')
.factory('AuthenticationService',
	    ['ENDPOINTS', 'Base64', '$http', '$cookieStore', '$rootScope', '$timeout',
	    function (Endpoints, Base64, $http, $cookieStore, $rootScope, $timeout) {
	        var service = {};

	        service.createContext = function (username, password, callback) {

	        	service.clearContext();

	        	var authdata = Base64.encode(username + ':' + password);

	            $rootScope.globals = {
	                currentUser: {
	                    username: username,
	                    authdata: authdata
	                }
	            };

	           $http.defaults.headers.common['Authorization'] = 'Basic ' + authdata;

	           $http.post(Endpoints.gviam +'/authenticate').then(function(response) {
	        	   			angular.merge($rootScope.globals.currentUser, response.data);

	        	   			if (response.data.roles) {
		        	   			for ( var item in response.data.roles) {

		        	   				$rootScope.globals.currentUser.isAdministrator = $rootScope.globals.currentUser.isAdministrator || (item == 'gvadmin');
			        	   			$rootScope.globals.currentUser.isAccountManager = $rootScope.globals.currentUser.isAccountManager || (item == 'gvmanager_account');
			        	   			$rootScope.globals.currentUser.isAccountClient = $rootScope.globals.currentUser.isAccountClient || (item == 'gvclient_account');
			        	   			$rootScope.globals.currentUser.isAccountGuest = $rootScope.globals.currentUser.isAccountGuest || (item == 'gvguest_account');
			        	   			$rootScope.globals.currentUser.isSchedulerManager = $rootScope.globals.currentUser.isSchedulerManager || (item == 'gvmanager_schedule');
			        	   			$rootScope.globals.currentUser.isSchedulerGuest = $rootScope.globals.currentUser.isSchedulerGuest || (item == 'gvguest_schedule');
			        	   			$rootScope.globals.currentUser.isConfigManager = $rootScope.globals.currentUser.isConfigManager || (item == 'gvmanager_config');
			        	   			$rootScope.globals.currentUser.isConfigGuest = $rootScope.globals.currentUser.isConfigGuest || (item == 'gvguest_config');

		        	   			}
	        	   			}
	        	   			$cookieStore.put('globals', $rootScope.globals);
	        	   			callback(response);
	           			},
	        		   function(response) {
	           				delete $http.defaults.headers.common.Authorization;
	           				$rootScope.globals = {};
	           				callback(response);
	           		   });

	        };

	        service.changePassword = function (username, oldPassword, newPassword, callback) {

	           var token = Base64.encode(username + ':' + oldPassword+ ':'+newPassword);

	           var request = {
	        		   method: 'PATCH',
	        		   url: Endpoints.gviam + '/authenticate',
	        		   headers: {
	        			   'x-renew-password': token,
	        			   Accept: 'application/json'
	        		   }
	           };

	           $http(request).then(function(response) {

	        	   			var authdata = Base64.encode(username + ':' + newPassword);

				            $rootScope.globals = {
				                currentUser: {
				                    username: username,
				                    authdata: authdata
				                }
				            };

				            $http.defaults.headers.common['Authorization'] = 'Basic ' + authdata;

	        	   			angular.merge($rootScope.globals.currentUser, response.data);
	        	   			$cookieStore.put('globals', $rootScope.globals);
	        	   			callback(response.status);
	           			},
	        		   function(response) {
	           				$rootScope.globals = {};
	           				callback(response.status);
	           		   });

	        };

	        service.clearContext = function () {
	            $rootScope.globals = {};
	            $cookieStore.remove('globals');
	            delete $http.defaults.headers.common.Authorization;
	        };

	        return service;
}]);

angular.module('gvconsole')
.controller('LoginController',
    ['$scope', '$rootScope', '$location', 'AuthenticationService',
    function ($scope, $rootScope, $location, authenticationService) {
        // reset login status
    	authenticationService.clearContext();

        $scope.login = function () {
            $scope.dataLoading = true;
            $scope.error = false;
            authenticationService.createContext($scope.username, $scope.password, function(response){
            	 $scope.dataLoading = false;
            	switch (response.status) {

            			case 200:
		                    $location.path('/myprofile');
		                    break;

	                	case 401:
	                		if (response.headers("X-Auth-Status") == "Expired") {
	                			$scope.expired=true;
	                			break;
	                		}

	                	default:
	                	    $scope.error = true;
	                    	$scope.errorMessage = 'Login failed';
	                    break;
                }
            });
        };

        $scope.changePassword = function () {
            $scope.dataLoading = true;
            $scope.error = false;
            authenticationService.changePassword($scope.username, $scope.password,  $scope.newPassword, function(status){
            	$scope.dataLoading = false;
            	switch (status) {
                	case 200:
	                    $location.path('/myprofile');
	                    break;

                    default:
	                		$scope.error = true;
	                    $scope.errorMessage = 'Password change failed';
	                    break;
                }
            });
        };
}]);
