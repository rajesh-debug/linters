#!/bin/sh

# cd or fail
cd "$GITHUB_WORKSPACE" || exit 1

if [ "$INPUT_FAIL_ON_ERROR" = true ] ; then
  echo "Fail on error set on pipefail"
  set -o pipefail
fi

export REVIEWDOG_GITHUB_API_TOKEN="${INPUT_GITHUB_TOKEN}"

if [ -f "$INPUT_DETEKT_CONFIG" ]
then
	echo "Local detekt config will override the udaan detekt config file"
else
	cp -rf /udaan-detekt-config.yml default-detekt-config.yml
fi


if [ -f "${INPUT_DETEKT_BASELINE}" ]
then
	echo "Baseline file detected, will be using the same"
  detekt --input . --config "${INPUT_DETEKT_CONFIG}" \
    --report xml:detekt_report.xml \
    --baseline "${INPUT_DETEKT_BASELINE}" \
    --includes "${INPUT_DETEKT_INCLUDES}" \
    --plugins /opt/detekt-formatting.jar
else
	echo "Analysis will continue without any baseline"
  detekt --input . --config "${INPUT_DETEKT_CONFIG}" \
    --report xml:detekt_report.xml \
    --includes "${INPUT_DETEKT_INCLUDES}" \
    --plugins /opt/detekt-formatting.jar
fi

reviewdog -f=checkstyle -name="detekt" -reporter="${INPUT_REVIEWDOG_REPORTER}" \
  -level="${INPUT_REVIEWDOG_LEVEL}" -filter-mode="${INPUT_REVIEWDOG_FILTER}" <detekt_report.xml
