These four lines assign what happens when the user interacts with each report card in submitted_reports_page.dart (line 614):

onTap: () => _openReport(report)
Tapping the whole card opens that report.

onEdit: () => _openReport(report)
Choosing Edit also opens the same report screen for editing/viewing.

onDelete: () => _deleteReport(report)
Choosing Delete shows a confirmation dialog, then calls the delete API for that report type and refreshes the list.

onSubmit: () => _openReport(report)
Choosing Submit currently does the same thing as tap/edit: it opens the report page. It does not submit directly from here.

What _openReport(report) does in submitted_reports_page.dart (line 681):

If type is Special Report, it opens SpecialReportPage
If type is VR-Workplace, it opens WorkplacePage
If type is VR-Personal, it opens PersonalPage
If type is VR-Academic, it opens AcademicPage
If type is Incident Report, it opens IncidentReportPage
After returning from those pages, it calls _fetchReports() to reload the list.

What _deleteReport(report) does in submitted_reports_page.dart (line 747):

Blocks delete for Incident Report
Shows Confirm Delete
Calls the matching delete API based on report type
Shows success/failure snackbar
Reloads the report list if delete succeeds

For Special Report: 
  Future<void> _fetchData() async {
    // If applicantData is provided, use it instead of fetching from API
    if (widget.applicantData != null && widget.applicantData!.isNotEmpty) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _populateFromApplicantData(widget.applicantData!);
          if (_allegations.isEmpty) {
            _allegations.add(AllegationItem(index: 1));
          }
        });
      }
      return;
    }

    try {
      final response = await ApiService.getSpecialReport(
        widget.specialReportId,
      );
      print('Special Report Response: $response');

      if (mounted) {
        setState(() {
          _isLoading = false;
          _formData = response;

          // Helper to get string safely (trying camelCase and PascalCase)
          String getString(String key) {
            // Try exact match
            if (response.containsKey(key))
              return response[key]?.toString() ?? '';
            // Try PascalCase
            if (key.isNotEmpty) {
              String pascal = key[0].toUpperCase() + key.substring(1);
              if (response.containsKey(pascal))
                return response[pascal]?.toString() ?? '';
            }
            return '';
          }

          // Mapping keys based on API response structure
          _reportTitleController.text = getString('title');

          String banglaName = getString('banglaName');
          String designation = getString('designation');
          _nameDesignationController.text = '$banglaName $designation'.trim();
          if (_nameDesignationController.text.isEmpty) {
            _nameDesignationController.text = getString('englishName');
          }

          _bpController.text = getString('bpNumber');
          _currentWorkplaceController.text = getString('currentWorkingPlace');

          String joiningDate = getString('joiningDateTime');
          if (joiningDate.isNotEmpty) {
            try {
              _joiningDateController.text = joiningDate.split('T')[0];
            } catch (e) {
              _joiningDateController.text = joiningDate;
            }
          }

          _previousWorkplaceController.text = getString('previousWorkPlace');
          _homeDistrictController.text = getString('homeDistrict');
          _mobileController.text = getString('phoneNumber');

          _punishmentController.text = getString('punishment');
          _rewardController.text = getString('reward');
          _irregularitiesController.text = getString('previousIrregularity');
          _othersInfoController.text = getString('othersInfo');
          _summaryController.text = getString('reportSummary');
          _recommendationController.text = getString('agentRecommendation');

          // Check if isSubmit is true
          if (response['isSubmit'] == true) {
            _isSubmitted = true;
          }

          // Allegations
          // Clear existing allegations to avoid duplicates if called multiple times or strictly set from API
          for (var allegation in _allegations) {
            allegation.dispose();
          }
          _allegations.clear();

          if (response.containsKey('vmSpecialReportComplains') &&
              response['vmSpecialReportComplains'] != null) {
            var complains = response['vmSpecialReportComplains'] as List;
            for (var i = 0; i < complains.length; i++) {
              var complain = complains[i];
              var item = AllegationItem(index: i + 1);
              // item.titleController.text = complain['title'] ?? '';

              // Fetch complain title if complainTypeId exists
              if (complain.containsKey('complainTypeId') &&
                  complain['complainTypeId'] != null) {
                int typeId = complain['complainTypeId'];
                item.complainTypeId = typeId;

                // Try to find in already loaded types or fetch specifically if needed
                // Since we are loading types async, we rely on _complainTypes being populated eventually
                // But for immediate display if types are not loaded yet, we might still want to fetch specific title
                // However, user requested dropdown usage. Dropdown value binding relies on item.complainTypeId matching a value in _complainTypes.

                // If we still want to show the title in the controller for fallback:
                ApiService.getSpecialReportComplainType(typeId).then((
                  typeResponse,
                ) {
                  if (typeResponse.containsKey('title') &&
                      typeResponse['title'] != null) {
                    if (mounted) {
                      setState(() {
                        item.titleController.text = typeResponse['title'];
                      });
                    }
                  }
                });
              }

              item.detailsController.text =
                  complain['details']?.toString() ?? '';
              item.witnessController.text =
                  complain['attestor']?.toString() ?? '';

              if (complain.containsKey('vmComplainAttachements') &&
                  complain['vmComplainAttachements'] != null) {
                item.existingAttachments = List<Map<String, dynamic>>.from(
                  complain['vmComplainAttachements'],
                );
                item.newAttachments.clear();
              }

              _allegations.add(item);
            }
          }

          // Ensure at least one allegation item exists
          /*if (_allegations.isEmpty) {
            _allegations.add(AllegationItem(index: 1));
          }*/
        });
      }
    } catch (e) {
      print('Error fetching data: $e');
      if (mounted) {
        setState(() {
          _isLoading = false;
          /*if (_allegations.isEmpty) {
              _allegations.add(AllegationItem(index: 1));
           }*/
        });
      }
    }
  }

  void _populateFromApplicantData(Map<String, dynamic> data) {
    // Helper to get string safely
    String getString(String key, {Map<String, dynamic>? source}) {
      final map = source ?? data;
      if (map.containsKey(key)) return map[key]?.toString() ?? '';
      // Try PascalCase
      if (key.isNotEmpty) {
        String pascal = key[0].toUpperCase() + key.substring(1);
        if (map.containsKey(pascal)) return map[pascal]?.toString() ?? '';
      }
      return '';
    }

    // Mapping keys from ComplainRegisterPage/ApplicantInfo
    // Expected keys: name, designation, bpNumber, mainUnit, joiningDate, previousWorkplace, homeDistrict, mobile, punishment, reward, irregularities

    // Name & Designation
    String name = getString('name');
    if (name.isEmpty) name = getString('english_name');
    String designation = getString('designation');
    if (designation.isEmpty) designation = getString('present_rank');

    _nameDesignationController.text = '$name $designation'.trim();

    // BP
    String bp = getString('bpNumber');
    if (bp.isEmpty) bp = getString('bp_number');
    _bpController.text = bp;

    // Current Workplace
    String currentWorkplace = getString('currentWorkplace');
    if (currentWorkplace.isEmpty) currentWorkplace = getString('main_unit');
    // Maybe concatenate with current_place_of_posting?
    String posting = getString('current_place_of_posting');
    if (posting.isNotEmpty) {
      if (currentWorkplace.isNotEmpty)
        currentWorkplace += ", $posting";
      else
        currentWorkplace = posting;
    }
    _currentWorkplaceController.text = currentWorkplace;

    // Joining Date
    String joiningDate = getString('joiningDate');
    if (joiningDate.isEmpty) joiningDate = getString('joining_date');
    if (joiningDate.isEmpty) joiningDate = getString('date_of_joining');
    if (joiningDate.isEmpty)
      joiningDate = getString('date_of_joining_at_present_rank');
    if (joiningDate.isNotEmpty) {
      try {
        _joiningDateController.text = joiningDate.split('T')[0];
      } catch (e) {
        _joiningDateController.text = joiningDate;
      }
    }

    // Previous Workplace
    _previousWorkplaceController.text = getString('previousWorkPlace');
    if (_previousWorkplaceController.text.isEmpty)
      _previousWorkplaceController.text = getString('previous_work_place');

    // Home District
    _homeDistrictController.text = getString('homeDistrict');
    if (_homeDistrictController.text.isEmpty)
      _homeDistrictController.text = getString('home_district');

    // Mobile
    _mobileController.text = getString('mobile');
    if (_mobileController.text.isEmpty)
      _mobileController.text = getString('mobile_no');
    if (_mobileController.text.isEmpty)
      _mobileController.text = getString('phone');

    // Punishment
    // Check if 'punishment' field exists and is not null/empty first
    // It seems previously we might have been checking list first or overwriting.
    String punishmentVal = '';
    if (data.containsKey('punishment') &&
        data['punishment'] != null &&
        data['punishment'].toString().isNotEmpty) {
      punishmentVal = data['punishment'].toString();
    } else {
      punishmentVal = getString('punishment');
    }

    // Clean up dots
    if (punishmentVal.contains('.......'))
      punishmentVal = punishmentVal.replaceAll('.', '').trim();
    _punishmentController.text = punishmentVal;

    // Reward
    String rewardVal = '';
    if (data.containsKey('reward') &&
        data['reward'] != null &&
        data['reward'].toString().isNotEmpty) {
      rewardVal = data['reward'].toString();
    } else {
      rewardVal = getString('reward');
    }

    // Clean up dots
    if (rewardVal.contains('.......'))
      rewardVal = rewardVal.replaceAll('.', '').trim();
    _rewardController.text = rewardVal;

    // Irregularities
    _irregularitiesController.text = getString('irregularities');
    if (_irregularitiesController.text.isEmpty)
      _irregularitiesController.text = getString('previousIrregularity');

    // Others Info
    _othersInfoController.text = getString('othersInfo');

    // Check if isSubmit is true
    if (data['isSubmit'] == true) {
      _isSubmitted = true;
    }

    // Populate Allegations
    if (data.containsKey('vmSpecialReportComplains') &&
        data['vmSpecialReportComplains'] != null) {
      var complains = data['vmSpecialReportComplains'] as List;
      _allegations.clear();
      for (var i = 0; i < complains.length; i++) {
        var complain = complains[i];
        var item = AllegationItem(index: i + 1);

        if (complain.containsKey('complainTypeId') &&
            complain['complainTypeId'] != null) {
          item.complainTypeId = complain['complainTypeId'];
        }
        item.titleController.text = complain['title'] ?? '';
        item.detailsController.text = complain['details']?.toString() ?? '';
        item.witnessController.text = complain['attestor']?.toString() ?? '';

        if (complain.containsKey('vmComplainAttachements') &&
            complain['vmComplainAttachements'] != null) {
          item.existingAttachments = List<Map<String, dynamic>>.from(
            complain['vmComplainAttachements'],
          );
          item.newAttachments.clear();
        }
        _allegations.add(item);
      }
    } else if (data.containsKey('complains_list') &&
        data['complains_list'] is List) {
      var list = data['complains_list'] as List;
      if (list.isNotEmpty) {
        _allegations.clear();
        for (var i = 0; i < list.length; i++) {
          var title = list[i].toString();
          var item = AllegationItem(index: i + 1);
          item.titleController.text = title;
          // Try to match if types are already loaded
          if (_complainTypes.isNotEmpty) {
            var match = _complainTypes.firstWhere(
              (t) => t['title'] == title,
              orElse: () => {},
            );
            if (match.isNotEmpty) {
              item.complainTypeId = match['complainTypeId'];
            }
          }
          _allegations.add(item);
        }
      }
    }
  }

  Future<void> _addAllegation() async {
    if (_complainTypes.isEmpty) {
      setState(() {
        _allegations.add(AllegationItem(index: _allegations.length + 1));
      });
      return;
    }

    final selected = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) {
        return AlertDialog(
          backgroundColor: Colors.white,
          title: const Text(
            'অভিযোগের ধরন নির্বাচন করুন',
            style: TextStyle(color: Colors.black),
          ),
          content: SizedBox(
            width: double.maxFinite,
            child: ListView.builder(
              shrinkWrap: true,
              itemCount: _complainTypes.length,
              itemBuilder: (context, index) {
                final type = _complainTypes[index];
                final title = type['title']?.toString() ?? '';
                return ListTile(
                  title: Text(
                    title,
                    style: const TextStyle(color: Colors.black),
                  ),
                  onTap: () {
                    Navigator.of(context).pop(type);
                  },
                );
              },
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Close', style: TextStyle(color: Colors.black)),
            ),
          ],
        );
      },
    );

    if (selected == null || selected.isEmpty) {
      return;
    }

    setState(() {
      final item = AllegationItem(index: _allegations.length + 1);
      item.complainTypeId = selected['complainTypeId'] as int?;
      item.titleController.text = selected['title']?.toString() ?? '';
      _allegations.add(item);
    });
  }

  void _removeAllegation(AllegationItem item) {
    setState(() {
      _allegations.remove(item);
      item.dispose();
      if (_allegations.isEmpty) {
        _allegations.add(AllegationItem(index: 1));
      }
      for (var i = 0; i < _allegations.length; i++) {
        _allegations[i].index = i + 1;
      }
    });
  }

  // Method to handle Save/Submit
  Future<void> _saveData({required bool isSubmit}) async {
    setState(() {
      _isLoading = true;
    });

    try {
      // Helper to safely get int
      int getInt(dynamic val) {
        if (val == null) return 0;
        return int.tryParse(val.toString()) ?? 0;
      }

      // Helper to safely get string from multiple sources
      String getString(
        String key, {
        List<String>? altKeys,
        TextEditingController? controller,
      }) {
        // 1. Prefer Controller if provided and not empty
        if (controller != null && controller.text.isNotEmpty) {
          return controller.text;
        }

        // 2. Try _formData (Edit mode)
        if (_formData.containsKey(key) && _formData[key] != null) {
          return _formData[key].toString();
        }

        // 3. Try Applicant Data
        if (widget.applicantData != null) {
          if (widget.applicantData!.containsKey(key))
            return widget.applicantData![key]?.toString() ?? '';

          if (altKeys != null) {
            for (var k in altKeys) {
              if (widget.applicantData!.containsKey(k))
                return widget.applicantData![k]?.toString() ?? '';
            }
          }
        }

        return '';
      }

      // Prepare fields
      int specialReportId = getInt(_formData['specialReportId']);
      int complainId = getInt(widget.complainId);
      if (complainId == 0) complainId = getInt(_formData['complainId']);

      // Complain No
      String complainNoStr = getString(
        'complainNo',
        altKeys: ['complain_ref_no', 'complainRefNo'],
      );
      dynamic complainNoVal = complainNoStr;
      if (int.tryParse(complainNoStr) != null) {
        complainNoVal = int.parse(complainNoStr);
      }

      // Title
      String title = _reportTitleController.text;

      // English Name & Bangla Name & Designation
      // Attempt to split name and designation from controller
      String nameDesignation = _nameDesignationController.text.trim();
      String banglaName = "";
      String designation = "";

      // Heuristic: split by last space to separate name and rank if combined
      // However, usually we can just rely on the existing variables or user input conventions.
      // If the controller has text, we should probably prioritize it.
      // Assuming format "Name Designation" or just "Name"
      // Since we don't have separate controllers, we might need to parse or just send as name?
      // But the API expects separate fields.

      // If we populated it as "$name $designation", we can try to respect that.
      // But if user edited it, it's hard to split.
      // Let's use the controller text for 'banglaName' (or englishName depending on field)
      // and keep designation from original data if possible, or try to extract.

      // Better approach:
      // If _nameDesignationController is not empty, use it for banglaName (assuming name field in UI is mapped to it).
      // But we also need 'designation'.

      banglaName = getString('banglaName', altKeys: ['bangla_name']);
      designation = getString('designation', altKeys: ['present_rank', 'rank']);

      if (_nameDesignationController.text.isNotEmpty) {
        // If the user modified the text, we treat the whole string as the Name
        // Or we can try to see if it contains the original designation at the end.
        String currentText = _nameDesignationController.text;
        if (designation.isNotEmpty && currentText.endsWith(designation)) {
          banglaName = currentText
              .substring(0, currentText.length - designation.length)
              .trim();
        } else {
          banglaName = currentText;
          // If designation is missing in text, maybe we should keep the original designation variable?
          // Yes, let's keep 'designation' as fetched/stored.
        }
      }

      String englishName = getString(
        'englishName',
        altKeys: ['english_name', 'name'],
      );

      // Dates
      // joiningDateTime
      String joiningDateTime = getString(
        'joiningDateTime',
        altKeys: ['joining_date', 'date_of_joining'],
      );
      // If controller has a date, use it (formatted)
      if (_joiningDateController.text.isNotEmpty) {
        try {
          // Try to parse and convert to ISO if it's not already
          DateTime dt = DateTime.parse(_joiningDateController.text);
          joiningDateTime = dt.toIso8601String();
        } catch (e) {
          joiningDateTime = _joiningDateController.text;
        }
      }

      // Previous Workplace
      String previousWorkPlace = getString(
        'previousWorkPlace',
        altKeys: ['previous_work_place'],
      );

      // Complain Relations for ID mapping
      List<dynamic> relationList = [];
      if (widget.applicantData != null &&
          widget.applicantData!.containsKey('complain_type_relations')) {
        relationList = widget.applicantData!['complain_type_relations'] as List;
      }

      // Construct Allegations List
      List<Map<String, dynamic>> vmSpecialReportComplains = [];

      for (var item in _allegations) {
        // Skip empty items if needed, or validate
        if (item.titleController.text.isEmpty &&
            item.detailsController.text.isEmpty)
          continue;

        int relationId = 0;
        // Find relation ID based on title
        if (relationList.isNotEmpty) {
          var match = relationList.firstWhere(
            (r) => r['complain_Type_Details'] == item.titleController.text,
            orElse: () => <String, dynamic>{},
          );
          if (match != null && match is Map && match.isNotEmpty) {
            // Logic for relation ID
            if (match.containsKey('complainComplainTypeRelationID')) {
              relationId = match['complainComplainTypeRelationID'] ?? 0;
            } else if (match.containsKey('id')) {
              relationId = match['id'] ?? 0;
            }
          }
        }

        List<Map<String, dynamic>> attachments = [];
        for (final a in item.newAttachments) {
          final t = a.titleController.text.trim();
          attachments.add({
            "specialReportComplainId": 0,
            "title": t,
            "filePath": a.path,
          });
        }
        // Also include existing attachments
        for (var att in item.existingAttachments) {
          attachments.add(att);
        }

        vmSpecialReportComplains.add({
          "specialReportComplainId": 0,
          "specialReportId": specialReportId,
          "complainTypeId": item.complainTypeId ?? 0,
          "complainComplainTypeRelationID": relationId,
          "title": item.titleController.text,
          "details": item.detailsController.text,
          "attestor": item.witnessController.text,
          "vmComplainAttachements": attachments,
        });
      }

      // Validate phone number
      String phoneNumber = _mobileController.text.trim();
      // Ensure phone number contains only digits (or standard phone characters like +)
      // A simple regex to keep only digits
      phoneNumber = phoneNumber.replaceAll(RegExp(r'[^0-9]'), '');

      Map<String, dynamic> requestBody = {
        "specialReportId": specialReportId,
        "complainId": complainId,
        "complainNo": complainNoVal, // Uses int if parsable, else string
        "title": title,
        "englishName": englishName,
        "banglaName": banglaName,
        "bpNumber": _bpController.text.trim(),
        "designation": designation,
        "currentWorkingPlace": _currentWorkplaceController.text,
        "joiningDateTime": joiningDateTime,
        "previousWorkPlace": previousWorkPlace,
        "homeDistrict": _homeDistrictController.text,
        "phoneNumber": phoneNumber,
        "punishment": _punishmentController.text,
        "reward": _rewardController.text,
        "previousIrregularity": _irregularitiesController.text,
        "othersInfo": _othersInfoController.text,
        "reportSummary": _summaryController.text,
        "agentRecommendation": _recommendationController.text,
        "authorityRecommendation": _formData['authorityRecommendation'],
        "isSubmit": isSubmit,
        "createAt": _formData['createAt'] ?? DateTime.now().toIso8601String(),
        "updatedAt": DateTime.now().toIso8601String(),
        "isDeleted": false,
        "vmSpecialReportComplains": vmSpecialReportComplains,
      };

      print('Special Report Request Body: $requestBody');

      // Use PUT if specialReportId is > 0
      final response = await ApiService.saveSpecialReport(
        requestBody,
        isUpdate: specialReportId > 0,
      );

      print('Special Report Save Response: $response');

      if (mounted) {
        setState(() {
          _isLoading = false;
        });

        if (response['statusCode'] == 1 || response['statusCode'] == 200) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'Special Report Saved Successfully',
                style: TextStyle(color: Colors.white),
              ),
              backgroundColor: Colors.green,
            ),
          );
          Navigator.pop(context, true);
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                'Failed to save: ${response['message'] ?? 'Unknown error'}',
              ),
            ),
          );
        }
      }
    } catch (e) {
      print('Error saving special report: $e');
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    }
  }

