#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
This script takes the path to a directory, and looks for any Turtle files
(https://www.w3.org/TeamSubmission/turtle/), then uses RDFLib to check if
they're valid TTL.

It exits with code 0 if all files are valid, 1 if not.
"""

import logging
import os

import daiquiri
import rdflib

daiquiri.setup(level=logging.INFO)

logger = daiquiri.getLogger(__name__)

# This is a slightly cheaty way of tracking which paths (if any) failed --
# we append to this global list, and inspect it at the end of the script!
failures = []


def parse_turtle(path):
    """
    Try to parse the Turtle at a given path.  Raises a ValueError if it fails!
    """
    logger.info("Parsing Turtle at path %s", path)
    graph = rdflib.Graph()
    try:
        graph.parse(path, format='ttl')
    except Exception as exc:
        # Get the name of the exception class
        # e.g. rdflib.plugins.parsers.notation3.BadSyntax
        exc_name = f'{exc.__class__.__module__}.{exc.__class__.__name__}'

        # Then try to log something useful
        logger.error("Error parsing Turtle (%s)", exc_name)
        logger.error(exc)

        failures.append(path)
    else:
        logger.info("Successfully parsed Turtle!")


if __name__ == '__main__':
    for root, _, filenames in os.walk('.'):
        for f in filenames:
            if not f.endswith('.ttl'):
                continue
            path = os.path.join(root, f)

            if 'WIP' in path:
                logger.info("Skipping path %s as WIP", path)
                continue

            parse_turtle(path)
